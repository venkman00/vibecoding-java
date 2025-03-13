package org.example.service;

import org.example.model.Address;
import org.example.model.Company;
import org.example.model.User;
import org.example.model.UserSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class UserTransformationServiceTest {
    
    private UserTransformationService service;
    private User user1;
    private User user2;
    private User user3;
    
    @BeforeEach
    void setUp() {
        service = new UserTransformationService();
        
        // Create test users
        user1 = createUser(1, "John Doe", "john@example.com", "New York", "Company A");
        user2 = createUser(2, "Jane Smith", "jane@test.com", "Los Angeles", "Company B");
        user3 = createUser(3, "Bob Johnson", "bob@example.com", "New York", "Company C");
    }
    
    @Test
    void transformUsers_shouldTransformAllUsers() {
        // Arrange
        List<User> users = Arrays.asList(user1, user2, user3);
        
        // Act
        List<UserSummary> result = service.transformUsers(users);
        
        // Assert
        assertEquals(3, result.size());
        verifyUserSummary(result.get(0), user1);
        verifyUserSummary(result.get(1), user2);
        verifyUserSummary(result.get(2), user3);
    }
    
    @Test
    void filterAndTransformUsers_shouldFilterAndTransformUsers() {
        // Arrange
        List<User> users = Arrays.asList(user1, user2, user3);
        Predicate<User> predicate = user -> "New York".equals(user.getAddress().getCity());
        
        // Act
        List<UserSummary> result = service.filterAndTransformUsers(users, predicate);
        
        // Assert
        assertEquals(2, result.size());
        verifyUserSummary(result.get(0), user1);
        verifyUserSummary(result.get(1), user3);
    }
    
    @Test
    void filterByEmailDomainAndTransform_shouldFilterByEmailDomain() {
        // Arrange
        List<User> users = Arrays.asList(user1, user2, user3);
        
        // Act
        List<UserSummary> result = service.filterByEmailDomainAndTransform(users, "example.com");
        
        // Assert
        assertEquals(2, result.size());
        verifyUserSummary(result.get(0), user1);
        verifyUserSummary(result.get(1), user3);
    }
    
    @Test
    void filterByEmailDomainAndTransform_shouldHandleNullEmails() {
        // Arrange
        User userWithNullEmail = createUser(4, "Null Email", null, "Chicago", "Company D");
        List<User> users = Collections.singletonList(userWithNullEmail);
        
        // Act
        List<UserSummary> result = service.filterByEmailDomainAndTransform(users, "example.com");
        
        // Assert
        assertEquals(0, result.size());
    }
    
    @Test
    void filterByCityAndTransform_shouldFilterByCity() {
        // Arrange
        List<User> users = Arrays.asList(user1, user2, user3);
        
        // Act
        List<UserSummary> result = service.filterByCityAndTransform(users, "New York");
        
        // Assert
        assertEquals(2, result.size());
        verifyUserSummary(result.get(0), user1);
        verifyUserSummary(result.get(1), user3);
    }
    
    @Test
    void filterByCityAndTransform_shouldHandleNullAddress() {
        // Arrange
        User userWithNullAddress = new User();
        userWithNullAddress.setId(4);
        userWithNullAddress.setName("Null Address");
        userWithNullAddress.setEmail("null@example.com");
        List<User> users = Collections.singletonList(userWithNullAddress);
        
        // Act
        List<UserSummary> result = service.filterByCityAndTransform(users, "New York");
        
        // Assert
        assertEquals(0, result.size());
    }
    
    private User createUser(int id, String name, String email, String city, String companyName) {
        User user = new User();
        user.setId(id);
        user.setName(name);
        user.setEmail(email);
        
        if (city != null) {
            Address address = new Address();
            address.setCity(city);
            user.setAddress(address);
        }
        
        if (companyName != null) {
            Company company = new Company();
            company.setName(companyName);
            user.setCompany(company);
        }
        
        return user;
    }
    
    private void verifyUserSummary(UserSummary summary, User user) {
        assertEquals(user.getId(), summary.getUserId());
        assertEquals(user.getName(), summary.getFullName());
        assertEquals(user.getEmail(), summary.getContactEmail());
        assertEquals(user.getAddress() != null ? user.getAddress().getCity() : "", summary.getLocation());
        assertEquals(user.getCompany() != null ? user.getCompany().getName() : "", summary.getOrganization());
    }
} 