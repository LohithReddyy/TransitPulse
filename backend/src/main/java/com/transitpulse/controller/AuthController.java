package com.transitpulse.controller;

import com.transitpulse.dto.ApiResponse;
import com.transitpulse.dto.auth.AuthResponse;
import com.transitpulse.dto.auth.LoginRequest;
import com.transitpulse.dto.auth.RegisterRequest;
import com.transitpulse.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    public AuthController(AuthService auth) { this.auth=auth; }
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(auth.register(request), "Account created successfully."));
    }
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(auth.login(request), "Login successful."));
    }
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<AuthResponse.UserProfile>> me(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok(auth.profile(authentication.getName())));
    }
}
