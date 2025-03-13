/*
 * This source file was generated by the Gradle 'init' task
 */
package org.example;

import org.example.model.Address;
import org.example.model.Company;
import org.example.model.User;
import org.example.model.UserSummary;
import org.example.service.ApiService;
import org.example.service.UserTransformationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppTest {
    
    @Mock
    private ApiService apiService;
    
    @Mock
    private UserTransformationService transformationService;
    
    private App app;
    
    @BeforeEach
    void setUp() {
        app = new App(apiService, transformationService);
    }
    
    @Test
    void fetchUsers_shouldCallApiService() throws IOException {
        // Arrange
        List<User> expectedUsers = Collections.singletonList(createTestUser());
        when(apiService.fetchUsers()).thenReturn(expectedUsers);
        
        // Act
        List<User> actualUsers = app.fetchUsers();
        
        // Assert
        assertEquals(expectedUsers, actualUsers);
        verify(apiService).fetchUsers();
    }
    
    @Test
    void transformUsers_shouldCallTransformationService() {
        // Arrange
        List<User> users = Collections.singletonList(createTestUser());
        List<UserSummary> expectedSummaries = Collections.singletonList(new UserSummary(createTestUser()));
        when(transformationService.transformUsers(users)).thenReturn(expectedSummaries);
        
        // Act
        List<UserSummary> actualSummaries = app.transformUsers(users);
        
        // Assert
        assertEquals(expectedSummaries, actualSummaries);
        verify(transformationService).transformUsers(users);
    }
    
    @Test
    void filterByEmailDomainAndTransform_shouldCallTransformationService() {
        // Arrange
        List<User> users = Arrays.asList(createTestUser(), createTestUser());
        String domain = "test.com";
        List<UserSummary> expectedSummaries = Collections.singletonList(new UserSummary(createTestUser()));
        when(transformationService.filterByEmailDomainAndTransform(users, domain)).thenReturn(expectedSummaries);
        
        // Act
        List<UserSummary> actualSummaries = app.filterByEmailDomainAndTransform(users, domain);
        
        // Assert
        assertEquals(expectedSummaries, actualSummaries);
        verify(transformationService).filterByEmailDomainAndTransform(users, domain);
    }
    
    @Test
    void postUserSummary_shouldCallApiService() throws IOException {
        // Arrange
        UserSummary userSummary = new UserSummary(createTestUser());
        when(apiService.postUserSummary(userSummary)).thenReturn(true);
        
        // Act
        boolean result = app.postUserSummary(userSummary);
        
        // Assert
        assertTrue(result);
        verify(apiService).postUserSummary(userSummary);
    }
    
    private User createTestUser() {
        User user = new User();
        user.setId(1);
        user.setName("John Doe");
        user.setUsername("johndoe");
        user.setEmail("john@example.com");
        
        Address address = new Address();
        address.setCity("New York");
        user.setAddress(address);
        
        Company company = new Company();
        company.setName("Test Company");
        user.setCompany(company);
        
        return user;
    }
}
