package com.cloud.computing.filesharingapp.security;

import com.cloud.computing.filesharingapp.entity.AccountStatus;
import com.cloud.computing.filesharingapp.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

/**
 * Spring Security UserDetails implementation for the file sharing application.
 * 
 * <p>This class wraps the User entity to provide Spring Security with the
 * necessary authentication and authorization information. It implements
 * additional security checks beyond basic authentication:
 * <ul>
 *   <li>Email verification requirement for account activation</li>
 *   <li>Account status validation (ACTIVE, PENDING, SUSPENDED, etc.)</li>
 *   <li>Integration with JWT token generation</li>
 * </ul>
 * 
 * <p>The {@code isEnabled()} method enforces that users must have both
 * verified their email and have an ACTIVE account status to authenticate.
 * 
 * @author File Sharing App Team
 * @version 1.0
 * @since 1.0
 */
public class UserPrincipal implements UserDetails {
    private Long id;
    private String username;
    private String email;
    private String password;
    private boolean emailVerified;
    private AccountStatus accountStatus;
    
    /**
     * Constructor for creating a UserPrincipal with all required fields.
     * 
     * @param id the user's unique identifier
     * @param username the user's username
     * @param email the user's email address
     * @param password the user's encrypted password
     * @param emailVerified whether the user's email has been verified
     * @param accountStatus the current status of the user's account
     */
    public UserPrincipal(Long id, String username, String email, String password, boolean emailVerified, AccountStatus accountStatus) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.password = password;
        this.emailVerified = emailVerified;
        this.accountStatus = accountStatus;
    }
    
    /**
     * Factory method to create a UserPrincipal from a User entity.
     * 
     * @param user the User entity to convert
     * @return UserPrincipal instance for Spring Security
     */
    public static UserPrincipal create(User user) {
        return new UserPrincipal(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getPassword(),
                user.isEmailVerified(),
                user.getAccountStatus()
        );
    }
    
    public Long getId() {
        return id;
    }
    
    public String getEmail() {
        return email;
    }
    
    public boolean isEmailVerified() {
        return emailVerified;
    }
    
    public AccountStatus getAccountStatus() {
        return accountStatus;
    }
    
    @Override
    public String getUsername() {
        return username;
    }
    
    @Override
    public String getPassword() {
        return password;
    }
    
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.emptyList();
    }
    
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }
    
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }
    
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
    
    /**
     * Determines if the user account is enabled for authentication.
     * 
     * <p>This method enforces the application's security policy by requiring:
     * <ul>
     *   <li>Email verification must be completed</li>
     *   <li>Account status must be ACTIVE</li>
     * </ul>
     * 
     * <p>Users with unverified emails or non-ACTIVE status (PENDING, SUSPENDED, etc.)
     * will be unable to authenticate even with correct credentials.
     * 
     * @return true if the user can authenticate, false otherwise
     */
    @Override
    public boolean isEnabled() {
        // Only allow login for verified users with ACTIVE status
        return emailVerified && accountStatus == AccountStatus.ACTIVE;
    }
}