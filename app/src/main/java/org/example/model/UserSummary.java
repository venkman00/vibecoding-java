package org.example.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Model class representing a transformed user summary for the POST request.
 */
@Data
@NoArgsConstructor
public class UserSummary {
    @JsonProperty("user_id")
    private int userId;
    
    @JsonProperty("full_name")
    private String fullName;
    
    @JsonProperty("contact_email")
    private String contactEmail;
    
    @JsonProperty("location")
    private String location;
    
    @JsonProperty("organization")
    private String organization;
    
    /**
     * Constructor for transformation.
     *
     * @param user the user to transform
     */
    public UserSummary(User user) {
        this.userId = user.getId();
        this.fullName = user.getName();
        this.contactEmail = user.getEmail();
        this.location = user.getAddress() != null ? user.getAddress().getCity() : "";
        this.organization = user.getCompany() != null ? user.getCompany().getName() : "";
    }
} 