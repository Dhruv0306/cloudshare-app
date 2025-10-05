package com.cloud.computing.filesharingapp.security;

import com.cloud.computing.filesharingapp.entity.AccountStatus;
import com.cloud.computing.filesharingapp.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

public class UserPrincipal implements UserDetails {
    private Long id;
    private String username;
    private String email;
    private String password;
    private boolean emailVerified;
    private AccountStatus accountStatus;
    
    public UserPrincipal(Long id, String username, String email, String password, boolean emailVerified, AccountStatus accountStatus) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.password = password;
        this.emailVerified = emailVerified;
        this.accountStatus = accountStatus;
    }
    
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
    
    @Override
    public boolean isEnabled() {
        // Only allow login for verified users with ACTIVE status
        return emailVerified && accountStatus == AccountStatus.ACTIVE;
    }
}