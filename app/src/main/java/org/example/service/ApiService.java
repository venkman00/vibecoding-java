package org.example.service;

import org.example.model.User;
import org.example.model.UserSummary;

import java.io.IOException;
import java.util.List;

/**
 * Interface for API operations.
 */
public interface ApiService {
    
    /**
     * Fetches users from the API.
     *
     * @return List of User objects
     * @throws IOException if an I/O error occurs
     */
    List<User> fetchUsers() throws IOException;
    
    /**
     * Posts a user summary to the API.
     *
     * @param userSummary the user summary to post
     * @return true if the post was successful, false otherwise
     * @throws IOException if an I/O error occurs
     */
    boolean postUserSummary(UserSummary userSummary) throws IOException;
} 