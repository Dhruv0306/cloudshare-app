package com.cloud.computing.filesharingapp.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.mockito.Mockito.*;

class RateLimitingFilterTest {

    @Mock
    private HttpServletRequest request;
    
    @Mock
    private HttpServletResponse response;
    
    @Mock
    private FilterChain filterChain;
    
    private RateLimitingFilter filter;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        filter = new RateLimitingFilter();
    }

    @Test
    void shouldAllowNonVerificationEndpoints() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/files");
        when(request.getMethod()).thenReturn("GET");
        
        filter.doFilterInternal(request, response, filterChain);
        
        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    void shouldAllowFirstVerificationAttempt() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/auth/verify-email");
        when(request.getMethod()).thenReturn("POST");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        
        filter.doFilterInternal(request, response, filterChain);
        
        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    void shouldAllowFirstResendRequest() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/auth/resend-verification");
        when(request.getMethod()).thenReturn("POST");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        
        filter.doFilterInternal(request, response, filterChain);
        
        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    void shouldBlockExcessiveVerificationAttempts() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/auth/verify-email");
        when(request.getMethod()).thenReturn("POST");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(printWriter);
        
        // Make 4 attempts (should block the 4th)
        for (int i = 0; i < 4; i++) {
            filter.doFilterInternal(request, response, filterChain);
        }
        
        // The 4th attempt should be blocked
        verify(response, atLeastOnce()).setStatus(429);
        verify(response, atLeastOnce()).setContentType("application/json");
    }
}