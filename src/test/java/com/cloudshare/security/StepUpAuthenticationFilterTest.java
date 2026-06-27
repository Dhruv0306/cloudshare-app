package com.cloudshare.security;

import com.cloudshare.model.Role;
import com.cloudshare.model.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StepUpAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private StepUpAuthenticationFilter stepUpAuthenticationFilter;

    @BeforeEach
    void setUp() {
        stepUpAuthenticationFilter = new StepUpAuthenticationFilter(tokenProvider);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void testNonAdminPathAllowed() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/files");
        
        stepUpAuthenticationFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(401);
    }

    @Test
    void testAdminPathUnauthenticatedAllowedToProceed() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/admin/users");
        
        stepUpAuthenticationFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(401);
    }

    @Test
    void testAdminPathAuthenticatedInvalidTokenBlocked() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/admin/users");
        
        User user = User.builder()
                .id(UUID.randomUUID())
                .username("admin")
                .email("admin@example.com")
                .roles(Collections.singleton(new Role(1L, "ROLE_ADMIN")))
                .build();
        UserPrincipal principal = new UserPrincipal(user);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        when(request.getHeader("X-StepUp-Token")).thenReturn("invalid-stepup-token");
        when(tokenProvider.validateStepUpToken("invalid-stepup-token", principal.getId().toString())).thenReturn(false);

        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(printWriter);

        stepUpAuthenticationFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain, never()).doFilter(request, response);
        verify(response).setStatus(401);
        verify(response).setContentType("application/json");
    }

    @Test
    void testAdminPathAuthenticatedValidTokenAllowed() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/admin/users");
        
        User user = User.builder()
                .id(UUID.randomUUID())
                .username("admin")
                .email("admin@example.com")
                .roles(Collections.singleton(new Role(1L, "ROLE_ADMIN")))
                .build();
        UserPrincipal principal = new UserPrincipal(user);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        when(request.getHeader("X-StepUp-Token")).thenReturn("valid-stepup-token");
        when(tokenProvider.validateStepUpToken("valid-stepup-token", principal.getId().toString())).thenReturn(true);

        stepUpAuthenticationFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(401);
    }
}
