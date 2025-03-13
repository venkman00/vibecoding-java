package org.example.service;

import lombok.extern.slf4j.Slf4j;
import org.example.model.User;
import org.example.model.UserSummary;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Service for transforming User data into UserSummary objects.
 */
@Slf4j
public class UserTransformationService {
    
    /**
     * Transforms a list of users into a list of user summaries.
     *
     * @param users the list of users to transform
     * @return a list of user summaries
     */
    public List<UserSummary> transformUsers(List<User> users) {
        log.info("Transforming {} users into user summaries", users.size());
        List<UserSummary> summaries = users.stream()
                .map(UserSummary::new)
                .collect(Collectors.toList());
        log.debug("Transformed {} users into user summaries", summaries.size());
        return summaries;
    }
    
    /**
     * Filters users based on a predicate and transforms them into user summaries.
     *
     * @param users the list of users to filter and transform
     * @param predicate the predicate to filter users
     * @return a list of filtered and transformed user summaries
     */
    public List<UserSummary> filterAndTransformUsers(List<User> users, Predicate<User> predicate) {
        log.info("Filtering and transforming {} users", users.size());
        List<UserSummary> summaries = users.stream()
                .filter(predicate)
                .map(UserSummary::new)
                .collect(Collectors.toList());
        log.debug("Filtered and transformed {} users into user summaries", summaries.size());
        return summaries;
    }
    
    /**
     * Filters users by email domain and transforms them into user summaries.
     *
     * @param users the list of users to filter and transform
     * @param domain the email domain to filter by
     * @return a list of filtered and transformed user summaries
     */
    public List<UserSummary> filterByEmailDomainAndTransform(List<User> users, String domain) {
        log.info("Filtering users by email domain: {} and transforming", domain);
        List<UserSummary> summaries = filterAndTransformUsers(users, user -> 
                user.getEmail() != null && user.getEmail().endsWith("@" + domain));
        log.debug("Found {} users with email domain: {}", summaries.size(), domain);
        return summaries;
    }
    
    /**
     * Filters users by city and transforms them into user summaries.
     *
     * @param users the list of users to filter and transform
     * @param city the city to filter by
     * @return a list of filtered and transformed user summaries
     */
    public List<UserSummary> filterByCityAndTransform(List<User> users, String city) {
        log.info("Filtering users by city: {} and transforming", city);
        List<UserSummary> summaries = filterAndTransformUsers(users, user -> 
                user.getAddress() != null && 
                city.equalsIgnoreCase(user.getAddress().getCity()));
        log.debug("Found {} users in city: {}", summaries.size(), city);
        return summaries;
    }
} 