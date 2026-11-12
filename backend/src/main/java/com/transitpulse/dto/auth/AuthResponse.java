package com.transitpulse.dto.auth;

import java.time.LocalDateTime;

public record AuthResponse(String token, String tokenType, LocalDateTime expiresAt, UserProfile user) {
    public record UserProfile(Long id, String fullName, String email) {}
}
