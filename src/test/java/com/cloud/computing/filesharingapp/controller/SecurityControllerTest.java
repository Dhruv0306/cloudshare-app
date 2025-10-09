package com.cloud.computing.filesharingapp.controller;

import com.cloud.computing.filesharingapp.service.AdvancedSecurityService;
import com.cloud.computing.filesharingapp.service.RateLimitingService;
import com.cloud.computing.filesharingapp.service.SecurityMonitoringService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for SecurityController endpoints.
 * 
 * <p>
 * Tests the security monitoring REST API endpoints including:
 * <ul>
 * <li>Security dashboard generation</li>
 * <li>Analytics and metrics retrieval</li>
 * <li>Threat level assessment</li>
 * <li>Rate limiting status checks</li>
 * <li>Administrative security controls</li>
 * </ul>
 * 
 * @author File Sharing App Team
 * @version 1.0
 * @since 1.0
 */
@SpringBootTest
@AutoConfigureWebMvc
@ActiveProfiles("test")
@Transactional
class SecurityControllerTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @MockBean
    private SecurityMonitoringService securityMonitoringService;

    @MockBean
    private AdvancedSecurityService advancedSecurityService;

    @MockBean
    private RateLimitingService rateLimitingService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testGetSecurityDashboard_Success() throws Exception {
        // Given
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime since = now.minusHours(24);

        AdvancedSecurityService.SecurityAnalytics mockAnalytics = new AdvancedSecurityService.SecurityAnalytics(
                100L, 60L, 40L, 10L, 8L, 0, 0, 0, new ArrayList<>(), since, now);

        RateLimitingService.RateLimitAnalytics mockRateLimitAnalytics = new RateLimitingService.RateLimitAnalytics(
                50, 5, 100L, 10L, 20, 5, since, now);

        SecurityMonitoringService.SecurityDashboard mockDashboard = new SecurityMonitoringService.SecurityDashboard(
                mockAnalytics, mockRateLimitAnalytics, new ArrayList<>(), new ArrayList<>(),
                null, new HashMap<>(), 95.0, since, now);

        when(securityMonitoringService.generateSecurityDashboard(anyInt()))
                .thenReturn(mockDashboard);

        // When & Then
        mockMvc.perform(get("/api/security/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.securityScore").value(95.0))
                .andExpect(jsonPath("$.securityAnalytics.totalAccesses").value(100))
                .andExpect(jsonPath("$.rateLimitAnalytics.totalRequests").value(50));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testGetSecurityAnalytics_Success() throws Exception {
        // Given
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime since = now.minusHours(24);

        AdvancedSecurityService.SecurityAnalytics mockAnalytics = new AdvancedSecurityService.SecurityAnalytics(
                100L, 60L, 40L, 10L, 8L, 0, 0, 0, new ArrayList<>(), since, now);

        when(advancedSecurityService.getSecurityAnalytics(anyInt()))
                .thenReturn(mockAnalytics);

        // When & Then
        mockMvc.perform(get("/api/security/analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAccesses").value(100))
                .andExpect(jsonPath("$.viewAccesses").value(60))
                .andExpect(jsonPath("$.downloadAccesses").value(40));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testGetThreatLevel_Success() throws Exception {
        // Given
        String ipAddress = "192.168.1.100";
        when(advancedSecurityService.getThreatLevel(ipAddress))
                .thenReturn(AdvancedSecurityService.SecurityThreatLevel.LOW);

        // When & Then
        mockMvc.perform(get("/api/security/threat-level/{ipAddress}", ipAddress))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ipAddress").value(ipAddress))
                .andExpect(jsonPath("$.threatLevel").value("LOW"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testGetRateLimitStatus_Success() throws Exception {
        // Given
        String ipAddress = "192.168.1.100";
        RateLimitingService.RateLimitStatus mockStatus = new RateLimitingService.RateLimitStatus(
                ipAddress, 10, 100, LocalDateTime.now().plusHours(1), false);

        when(rateLimitingService.getRateLimitStatus(ipAddress))
                .thenReturn(mockStatus);

        // When & Then
        mockMvc.perform(get("/api/security/rate-limit-status/{ipAddress}", ipAddress))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identifier").value(ipAddress))
                .andExpect(jsonPath("$.currentCount").value(10))
                .andExpect(jsonPath("$.limit").value(100))
                .andExpect(jsonPath("$.limited").value(false));
    }

    @Test
    @WithMockUser(roles = "USER") // Not ADMIN
    void testGetSecurityDashboard_Forbidden() throws Exception {
        // When & Then - Should be forbidden due to insufficient role
        mockMvc.perform(get("/api/security/dashboard"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testGetSecurityDashboard_Unauthorized() throws Exception {
        // When & Then (no authentication)
        mockMvc.perform(get("/api/security/dashboard"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testGetSecurityHealth_Success() throws Exception {
        // Given
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime since = now.minusHours(1);

        AdvancedSecurityService.SecurityAnalytics mockAnalytics = new AdvancedSecurityService.SecurityAnalytics(
                50L, 30L, 20L, 5L, 4L, 0, 2, 1, new ArrayList<>(), since, now);

        RateLimitingService.RateLimitAnalytics mockRateLimitAnalytics = new RateLimitingService.RateLimitAnalytics(
                25, 2, 50L, 5L, 10, 3, since, now);

        when(advancedSecurityService.getSecurityAnalytics(1))
                .thenReturn(mockAnalytics);
        when(rateLimitingService.getAnalytics(1))
                .thenReturn(mockRateLimitAnalytics);

        // When & Then
        mockMvc.perform(get("/api/security/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HEALTHY"))
                .andExpect(jsonPath("$.blacklistedIps").value(2))
                .andExpect(jsonPath("$.highThreatIps").value(1))
                .andExpect(jsonPath("$.rateLimitedRequests").value(2));
    }
}