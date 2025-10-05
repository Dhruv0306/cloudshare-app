package com.cloud.computing.filesharingapp.security;

import com.cloud.computing.filesharingapp.entity.AccountStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.mockito.Mockito.*;

class EmailVerificationFilterTest {

    @Mock
    private HttpServletRequest request;
    
    @Mock
    private HttpServletResponse response;
    
    @Mock
    private FilterChain filterChain;
    
    @Mock
    private Authentication authentication;
    
    @Mock
    private SecurityContext securityContext;
    
    private EmailVerificationFilter filter;
    @SuppressWarnings("unused")
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        filter = new EmailVerificationFilter();
        objectMapper = new ObjectMapper();
        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    void shouldAllowPublicEndpoints() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/auth/signin");
        
        filter.doFilterInternal(request, response, filterChain);
        
        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    void shouldAllowVerifiedUsers() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/files");
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        
        UserPrincipal userPrincipal = new UserPrincipal(1L, "testuser", "test@example.com", 
                                                        "password", true, AccountStatus.ACTIVE);
        when(authentication.getPrincipal()).thenReturn(userPrincipal);
        
        filter.doFilterInternal(request, response, filterChain);
        
        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    void shouldBlockUnverifiedUsers() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/files");
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        
        UserPrincipal userPrincipal = new UserPrincipal(1L, "testuser", "test@example.com", 
                                                        "password", false, AccountStatus.PENDING);
        when(authentication.getPrincipal()).thenReturn(userPrincipal);
        
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(printWriter);
        
        filter.doFilterInternal(request, response, filterChain);
        
        verify(response).setStatus(403);
        verify(response).setContentType("application/json");
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void shouldBlockSuspendedUsers() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/files");
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        
        UserPrincipal userPrincipal = new UserPrincipal(1L, "testuser", "test@example.com", 
                                                        "password", true, AccountStatus.SUSPENDED);
        when(authentication.getPrincipal()).thenReturn(userPrincipal);
        
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(printWriter);
        
        filter.doFilterInternal(request, response, filterChain);
        
        verify(response).setStatus(403);
        verify(response).setContentType("application/json");
        verify(filterChain, never()).doFilter(request, response);
    }
}