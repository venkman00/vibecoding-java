package org.example.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.example.config.MetricsConfig;
import org.example.exception.ApiException;
import org.example.model.User;
import org.example.model.UserSummary;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Implementation of the ApiService interface with resilience patterns.
 */
@Slf4j
public class ApiServiceImpl implements ApiService {
    
    private static final String BASE_URL = "https://jsonplaceholder.typicode.com";
    private static final String USERS_ENDPOINT = "/users";
    private static final String POST_ENDPOINT = "/posts";
    
    private final OkHttpClient client;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    
    /**
     * Constructor with default OkHttpClient and ObjectMapper.
     */
    public ApiServiceImpl() {
        this(new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build(),
             new ObjectMapper(),
             MetricsConfig.getRegistry());
    }
    
    /**
     * Constructor with custom OkHttpClient, ObjectMapper, and MeterRegistry.
     *
     * @param client the OkHttpClient to use
     * @param objectMapper the ObjectMapper to use
     * @param meterRegistry the MeterRegistry to use for metrics
     */
    public ApiServiceImpl(OkHttpClient client, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        
        // Configure circuit breaker
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMillis(1000))
                .permittedNumberOfCallsInHalfOpenState(2)
                .slidingWindowSize(10)
                .build();
        
        CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.of(circuitBreakerConfig);
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("apiService");
        
        // Configure retry
        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(500))
                .retryOnResult(response -> response instanceof List && ((List<?>) response).isEmpty())
                .retryExceptions(IOException.class)
                .build();
        
        RetryRegistry retryRegistry = RetryRegistry.of(retryConfig);
        this.retry = retryRegistry.retry("apiService");
        
        log.info("ApiServiceImpl initialized with circuit breaker and retry configuration");
    }
    
    @Override
    public List<User> fetchUsers() throws IOException {
        log.info("Fetching users from API");
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            // Decorate the call with retry and circuit breaker
            Supplier<List<User>> decoratedSupplier = CircuitBreaker.decorateSupplier(
                    circuitBreaker, 
                    () -> {
                        try {
                            return fetchUsersInternal();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
            
            decoratedSupplier = Retry.decorateSupplier(retry, decoratedSupplier);
            
            List<User> users = decoratedSupplier.get();
            log.info("Successfully fetched {} users from API", users.size());
            
            sample.stop(meterRegistry.timer("api.fetch.users.time"));
            meterRegistry.counter("api.fetch.users.count").increment();
            
            return users;
        } catch (Exception e) {
            sample.stop(meterRegistry.timer("api.fetch.users.error.time"));
            meterRegistry.counter("api.fetch.users.error.count").increment();
            
            if (e instanceof RuntimeException && e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            }
            
            if (e instanceof IOException) {
                log.error("Failed to fetch users from API", e);
                throw (IOException) e;
            }
            
            log.error("Unexpected error when fetching users from API", e);
            throw new ApiException("Failed to fetch users", 500, USERS_ENDPOINT, e);
        }
    }
    
    /**
     * Internal method to fetch users from the API.
     *
     * @return a list of users
     * @throws IOException if an I/O error occurs
     */
    private List<User> fetchUsersInternal() throws IOException {
        Request request = new Request.Builder()
                .url(BASE_URL + USERS_ENDPOINT)
                .get()
                .build();
        
        log.debug("Sending request to {}", request.url());
        
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.error("Received error response: {}", response);
                throw new ApiException(
                        "Unexpected response code: " + response.code(),
                        response.code(),
                        USERS_ENDPOINT);
            }
            
            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                log.error("Response body is null");
                throw new ApiException("Response body is null", 500, USERS_ENDPOINT);
            }
            
            String responseString = responseBody.string();
            log.debug("Received response: {}", responseString);
            
            return objectMapper.readValue(responseString, new TypeReference<List<User>>() {});
        }
    }
    
    @Override
    public boolean postUserSummary(UserSummary userSummary) throws IOException {
        log.info("Posting user summary for user: {}", userSummary.getFullName());
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            // Decorate the call with retry and circuit breaker
            Supplier<Boolean> decoratedSupplier = CircuitBreaker.decorateSupplier(
                    circuitBreaker, 
                    () -> {
                        try {
                            return postUserSummaryInternal(userSummary);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
            
            decoratedSupplier = Retry.decorateSupplier(retry, decoratedSupplier);
            
            boolean result = decoratedSupplier.get();
            log.info("Successfully posted user summary for user: {}", userSummary.getFullName());
            
            sample.stop(meterRegistry.timer("api.post.user.time"));
            meterRegistry.counter("api.post.user.count").increment();
            
            return result;
        } catch (Exception e) {
            sample.stop(meterRegistry.timer("api.post.user.error.time"));
            meterRegistry.counter("api.post.user.error.count").increment();
            
            if (e instanceof RuntimeException && e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            }
            
            if (e instanceof IOException) {
                log.error("Failed to post user summary", e);
                throw (IOException) e;
            }
            
            log.error("Unexpected error when posting user summary", e);
            throw new ApiException("Failed to post user summary", 500, POST_ENDPOINT, e);
        }
    }
    
    /**
     * Internal method to post a user summary to the API.
     *
     * @param userSummary the user summary to post
     * @return true if the post was successful, false otherwise
     * @throws IOException if an I/O error occurs
     */
    private boolean postUserSummaryInternal(UserSummary userSummary) throws IOException {
        String json = objectMapper.writeValueAsString(userSummary);
        
        RequestBody requestBody = RequestBody.create(
                MediaType.parse("application/json"), json);
        
        Request request = new Request.Builder()
                .url(BASE_URL + POST_ENDPOINT)
                .post(requestBody)
                .build();
        
        log.debug("Sending request to {} with body: {}", request.url(), json);
        
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.error("Received error response: {}", response);
                throw new ApiException(
                        "Unexpected response code: " + response.code(),
                        response.code(),
                        POST_ENDPOINT);
            }
            
            return true;
        }
    }
} 