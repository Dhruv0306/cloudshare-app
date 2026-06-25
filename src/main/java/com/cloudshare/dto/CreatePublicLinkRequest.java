package com.cloudshare.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePublicLinkRequest {
    @NotNull(message = "File ID is required")
    private UUID fileId;

    @NotNull(message = "Expiration in seconds is required")
    @Min(value = 1, message = "Expiration must be at least 1 second")
    private Long expiresInSeconds;

    private String password;

    @Min(value = 1, message = "Download limit must be at least 1")
    private Integer downloadLimit;
}
