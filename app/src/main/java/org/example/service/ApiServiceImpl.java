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
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * Implementation of the ApiService interface with resilience patterns.
 * Optimized for high throughput (10k TPS) with connection pooling and async processing.
 */
@Slf4j
public class ApiServiceImpl implements ApiService {
    
    private static final String BASE_URL = "https://jsonplaceholder.typicode.com";
    private static final String USERS_ENDPOINT = "/users";
    private static final String POST_ENDPOINT = "/posts";
    
    // Connection pool configuration for high throughput
    private static final int MAX_IDLE_CONNECTIONS = 100;
    private static final int KEEP_ALIVE_DURATION_MS = 30_000;
    private static final int CONNECTION_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 10_000;
    private static final int WRITE_TIMEOUT_MS = 10_000;
    
    // Thread pool for async operations
    private static final int CORE_POOL_SIZE = Runtime.getRuntime().availableProcessors() * 2;
    private static final int MAX_POOL_SIZE = Runtime.getRuntime().availableProcessors() * 4;
    private static final int QUEUE_CAPACITY = 10_000;
    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(
            CORE_POOL_SIZE,
            MAX_POOL_SIZE,
            60L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(QUEUE_CAPACITY),
            new ThreadPoolExecutor.CallerRunsPolicy());
    
    private final OkHttpClient client;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final Dispatcher dispatcher;
    
    /**
     * Constructor with default OkHttpClient and ObjectMapper.
     */
    public ApiServiceImpl() {
        this(createDefaultHttpClient(),
             createOptimizedObjectMapper(),
             MetricsConfig.getRegistry());
    }
    
    /**
     * Creates a default HTTP client optimized for high throughput.
     * 
     * @return an optimized OkHttpClient
     */
    private static OkHttpClient createDefaultHttpClient() {
        // Create a connection pool for connection reuse
        ConnectionPool connectionPool = new ConnectionPool(
                MAX_IDLE_CONNECTIONS,
                KEEP_ALIVE_DURATION_MS,
                TimeUnit.MILLISECONDS);
        
        // Create a dispatcher with increased max requests
        Dispatcher dispatcher = new Dispatcher(EXECUTOR);
        dispatcher.setMaxRequests(1000);
        dispatcher.setMaxRequestsPerHost(100);
        
        return new OkHttpClient.Builder()
                .connectionPool(connectionPool)
                .dispatcher(dispatcher)
                .connectTimeout(CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .writeTimeout(WRITE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .build();
    }
    
    /**
     * Creates an optimized ObjectMapper for high-performance JSON processing.
     * 
     * @return an optimized ObjectMapper
     */
    private static ObjectMapper createOptimizedObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // Add any performance optimizations for the ObjectMapper
        return mapper;
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
        this.dispatcher = client.dispatcher();
        
        // Configure circuit breaker with optimized settings for high throughput
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMillis(1000))
                .permittedNumberOfCallsInHalfOpenState(10)
                .slidingWindowSize(100)
                .minimumNumberOfCalls(20)
                .build();
        
        CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.of(circuitBreakerConfig);
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("apiService");
        
        // Configure retry with optimized settings for high throughput
        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(100))
                .retryOnResult(response -> response instanceof List && ((List<?>) response).isEmpty())
                .retryExceptions(IOException.class)
                .build();
        
        RetryRegistry retryRegistry = RetryRegistry.of(retryConfig);
        this.retry = retryRegistry.retry("apiService");
        
        log.info("ApiServiceImpl initialized with high-throughput configuration");
        log.info("Thread pool: core={}, max={}, queue={}", CORE_POOL_SIZE, MAX_POOL_SIZE, QUEUE_CAPACITY);
        log.info("Connection pool: maxIdleConnections={}, keepAliveDuration={}ms", 
                MAX_IDLE_CONNECTIONS, KEEP_ALIVE_DURATION_MS);
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
     * Asynchronously fetch users from the API.
     * 
     * @return CompletableFuture containing a list of users
     */
    public CompletableFuture<List<User>> fetchUsersAsync() {
        log.info("Asynchronously fetching users from API");
        Timer.Sample sample = Timer.start(meterRegistry);
        
        return CompletableFuture.supplyAsync(() -> {
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
                log.info("Successfully fetched {} users from API asynchronously", users.size());
                
                sample.stop(meterRegistry.timer("api.fetch.users.async.time"));
                meterRegistry.counter("api.fetch.users.async.count").increment();
                
                return users;
            } catch (Exception e) {
                sample.stop(meterRegistry.timer("api.fetch.users.async.error.time"));
                meterRegistry.counter("api.fetch.users.async.error.count").increment();
                
                log.error("Error in async user fetch", e);
                throw new CompletionException(e);
            }
        }, EXECUTOR);
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
     * Asynchronously post a user summary to the API.
     * 
     * @param userSummary the user summary to post
     * @return CompletableFuture containing the result of the operation
     */
    public CompletableFuture<Boolean> postUserSummaryAsync(UserSummary userSummary) {
        log.info("Asynchronously posting user summary for user: {}", userSummary.getFullName());
        Timer.Sample sample = Timer.start(meterRegistry);
        
        return CompletableFuture.supplyAsync(() -> {
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
                log.info("Successfully posted user summary for user: {} asynchronously", 
                        userSummary.getFullName());
                
                sample.stop(meterRegistry.timer("api.post.user.async.time"));
                meterRegistry.counter("api.post.user.async.count").increment();
                
                return result;
            } catch (Exception e) {
                sample.stop(meterRegistry.timer("api.post.user.async.error.time"));
                meterRegistry.counter("api.post.user.async.error.count").increment();
                
                log.error("Error in async user summary post", e);
                throw new CompletionException(e);
            }
        }, EXECUTOR);
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
    
    /**
     * Get the current number of active connections.
     * Useful for monitoring connection usage.
     * 
     * @return the number of active connections
     */
    public int getActiveConnectionCount() {
        return dispatcher.runningCallsCount();
    }
    
    /**
     * Get the current number of queued requests.
     * Useful for monitoring backpressure.
     * 
     * @return the number of queued requests
     */
    public int getQueuedRequestCount() {
        return dispatcher.queuedCallsCount();
    }
    
    /**
     * Gracefully shutdown the service, ensuring all pending requests are completed.
     */
    public void shutdown() {
        log.info("Shutting down ApiServiceImpl");
        
        // Shutdown the executor service
        EXECUTOR.shutdown();
        try {
            if (!EXECUTOR.awaitTermination(30, TimeUnit.SECONDS)) {
                EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        // Shutdown the OkHttp dispatcher
        dispatcher.executorService().shutdown();
        
        log.info("ApiServiceImpl shutdown complete");
    }
} 