package com.cloud.computing.filesharingapp.security;

import com.cloud.computing.filesharingapp.entity.User;
import com.cloud.computing.filesharingapp.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of Spring Security's UserDetailsService for loading user information.
 * 
 * <p>This service integrates with Spring Security's authentication framework by
 * providing user details from the application's database. It loads user information
 * based on username and converts it to a UserPrincipal object that implements
 * Spring Security's UserDetails interface.
 * 
 * <p>The service is used during authentication to:
 * <ul>
 *   <li>Validate user credentials against stored data</li>
 *   <li>Load user authorities and account status</li>
 *   <li>Create security context for authenticated users</li>
 * </ul>
 * 
 * @author File Sharing App Team
 * @version 1.0
 * @since 1.0
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {
    
    /** Repository for accessing user data from the database */
    @Autowired
    UserRepository userRepository;
    
    /**
     * Loads user details by username for Spring Security authentication.
     * 
     * <p>This method is called by Spring Security during authentication to
     * retrieve user information from the database. It finds the user by
     * username and converts the User entity to a UserPrincipal object
     * that contains all necessary authentication and authorization details.
     * 
     * <p>The method is transactional to ensure consistent data access and
     * to handle lazy-loaded relationships properly.
     * 
     * @param username the username to search for (case-sensitive)
     * @return UserDetails object containing user information and authorities
     * @throws UsernameNotFoundException if no user is found with the given username
     */
    @Override
    @Transactional
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Find user by username in the database
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User Not Found: " + username));
        
        // Convert User entity to UserPrincipal (UserDetails implementation)
        return UserPrincipal.create(user);
    }
}