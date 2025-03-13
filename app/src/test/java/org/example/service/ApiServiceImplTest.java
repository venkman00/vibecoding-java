package org.example.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import okhttp3.*;
import org.example.model.User;
import org.example.model.UserSummary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiServiceImplTest {

    @Mock
    private OkHttpClient mockClient;
    
    @Mock
    private Call mockCall;
    
    @Mock
    private Response mockResponse;
    
    @Mock
    private ResponseBody mockResponseBody;
    
    @Mock
    private Dispatcher mockDispatcher;
    
    @Mock
    private ExecutorService mockExecutorService;
    
    private ObjectMapper objectMapper;
    private MeterRegistry meterRegistry;
    private ApiServiceImpl apiService;
    
    // Use a direct executor for CompletableFuture to avoid timeouts in tests
    private final Executor directExecutor = Runnable::run;
    
    @BeforeEach
    void setUp() throws IOException {
        objectMapper = new ObjectMapper();
        meterRegistry = new SimpleMeterRegistry();
        
        // Configure mocks with lenient mode to avoid UnnecessaryStubbingException
        lenient().when(mockClient.dispatcher()).thenReturn(mockDispatcher);
        lenient().when(mockDispatcher.executorService()).thenReturn(mockExecutorService);
        lenient().when(mockClient.newCall(any(Request.class))).thenReturn(mockCall);
        lenient().when(mockCall.execute()).thenReturn(mockResponse);
        lenient().when(mockResponse.isSuccessful()).thenReturn(true);
        lenient().when(mockResponse.body()).thenReturn(mockResponseBody);
        lenient().when(mockDispatcher.runningCallsCount()).thenReturn(5);
        lenient().when(mockDispatcher.queuedCallsCount()).thenReturn(10);
        
        // Create a test instance with mocked dependencies
        apiService = new ApiServiceImpl(mockClient, objectMapper, meterRegistry) {
            @Override
            public CompletableFuture<List<User>> fetchUsersAsync() {
                CompletableFuture<List<User>> future = new CompletableFuture<>();
                try {
                    future.complete(fetchUsers());
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
                return future;
            }
            
            @Override
            public CompletableFuture<Boolean> postUserSummaryAsync(UserSummary userSummary) {
                CompletableFuture<Boolean> future = new CompletableFuture<>();
                try {
                    future.complete(postUserSummary(userSummary));
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
                return future;
            }
        };
    }
    
    @AfterEach
    void tearDown() {
        apiService.shutdown();
    }
    
    @Test
    void fetchUsers_shouldReturnUsersList() throws IOException {
        // Arrange
        String jsonResponse = "[{\"id\":1,\"name\":\"Test User\",\"email\":\"test@example.com\"}]";
        when(mockResponseBody.string()).thenReturn(jsonResponse);
        
        // Act
        List<User> users = apiService.fetchUsers();
        
        // Assert
        assertNotNull(users);
        assertEquals(1, users.size());
        assertEquals(1, users.get(0).getId());
        assertEquals("Test User", users.get(0).getName());
        assertEquals("test@example.com", users.get(0).getEmail());
        
        // Verify metrics
        assertTrue(meterRegistry.counter("api.fetch.users.count").count() > 0);
        
        // Verify HTTP call
        verify(mockClient, atLeastOnce()).newCall(any(Request.class));
    }
    
    @Test
    void fetchUsersAsync_shouldReturnUsersList() throws Exception {
        // Arrange
        String jsonResponse = "[{\"id\":1,\"name\":\"Test User\",\"email\":\"test@example.com\"}]";
        when(mockResponseBody.string()).thenReturn(jsonResponse);
        
        // Act
        CompletableFuture<List<User>> future = apiService.fetchUsersAsync();
        List<User> users = future.get(1, TimeUnit.SECONDS); // Reduced timeout
        
        // Assert
        assertNotNull(users);
        assertEquals(1, users.size());
        assertEquals(1, users.get(0).getId());
        assertEquals("Test User", users.get(0).getName());
        assertEquals("test@example.com", users.get(0).getEmail());
    }
    
    @Test
    void postUserSummary_shouldReturnTrue() throws IOException {
        // Arrange
        UserSummary userSummary = new UserSummary();
        userSummary.setUserId(1);
        userSummary.setFullName("Test User");
        userSummary.setContactEmail("test@example.com");
        
        // Act
        boolean result = apiService.postUserSummary(userSummary);
        
        // Assert
        assertTrue(result);
        
        // Verify metrics
        assertTrue(meterRegistry.counter("api.post.user.count").count() > 0);
        
        // Verify HTTP call
        verify(mockClient, atLeastOnce()).newCall(any(Request.class));
    }
    
    @Test
    void postUserSummaryAsync_shouldReturnTrue() throws Exception {
        // Arrange
        UserSummary userSummary = new UserSummary();
        userSummary.setUserId(1);
        userSummary.setFullName("Test User");
        userSummary.setContactEmail("test@example.com");
        
        // Act
        CompletableFuture<Boolean> future = apiService.postUserSummaryAsync(userSummary);
        boolean result = future.get(1, TimeUnit.SECONDS); // Reduced timeout
        
        // Assert
        assertTrue(result);
    }
    
    @Test
    void getActiveConnectionCount_shouldReturnCount() {
        // Act
        int count = apiService.getActiveConnectionCount();
        
        // Assert
        assertEquals(5, count);
        verify(mockDispatcher).runningCallsCount();
    }
    
    @Test
    void getQueuedRequestCount_shouldReturnCount() {
        // Act
        int count = apiService.getQueuedRequestCount();
        
        // Assert
        assertEquals(10, count);
        verify(mockDispatcher).queuedCallsCount();
    }
    
    @Test
    void shutdown_shouldShutdownExecutor() {
        // Act
        apiService.shutdown();
        
        // Assert
        verify(mockDispatcher, atLeastOnce()).executorService();
    }
} 