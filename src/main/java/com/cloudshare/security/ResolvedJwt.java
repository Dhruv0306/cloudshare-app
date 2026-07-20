package com.cloudshare.security;

public record ResolvedJwt(
        String token,
        boolean valid,
        String userId,
        String tokenType,
        String jti) {
    public static final String REQUEST_ATTRIBUTE = "com.cloudshare.security.RESOLVED_JWT";
}
