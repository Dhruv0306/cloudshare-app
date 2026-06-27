package com.cloudshare.controller;

import com.cloudshare.dto.*;
import com.cloudshare.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.cloudshare.security.UserPrincipal;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<String>> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest servletRequest) {
        authService.registerUser(request, getClientIp(servletRequest));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("User registered successfully."));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        AuthService.AuthResponseAndCookie result = authService.login(request, getClientIp(servletRequest));
        
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, result.getCookie().toString())
                .body(ApiResponse.success(result.getAuthResponse()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenRefreshResponse>> refresh(
            @CookieValue(name = "refresh_token", required = false) String refreshToken,
            HttpServletRequest servletRequest) {
        
        AuthService.TokenRefreshResponseAndCookie result = authService.refresh(refreshToken, getClientIp(servletRequest));
        
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, result.getCookie().toString())
                .body(ApiResponse.success(result.getRefreshResponse()));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<String>> logout(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @CookieValue(name = "refresh_token", required = false) String refreshToken,
            HttpServletRequest servletRequest) {
        
        ResponseCookie cookie = authService.logout(authHeader, refreshToken, getClientIp(servletRequest));
        
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.success("Logout successful"));
    }

    @PostMapping("/mfa/setup")
    public ResponseEntity<ApiResponse<MfaSetupResponse>> setupMfa(@AuthenticationPrincipal UserPrincipal principal) {
        MfaSetupResponse response = authService.setupMfa(principal.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/mfa/verify")
    public ResponseEntity<ApiResponse<String>> verifyMfa(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody MfaVerifyRequest request,
            HttpServletRequest servletRequest) {
        authService.verifyMfa(principal.getId(), request.getCode(), getClientIp(servletRequest));
        return ResponseEntity.ok(ApiResponse.success("Multi-Factor Authentication enabled successfully."));
    }

    @PostMapping("/mfa/step-up")
    public ResponseEntity<ApiResponse<MfaStepUpResponse>> stepUpMfa(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody MfaVerifyRequest request) {
        MfaStepUpResponse response = authService.stepUpMfa(principal.getId(), request.getCode());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
