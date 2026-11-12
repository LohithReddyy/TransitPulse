package com.transitpulse.service;

import com.transitpulse.dto.auth.AuthResponse;
import com.transitpulse.dto.auth.LoginRequest;
import com.transitpulse.dto.auth.RegisterRequest;
import com.transitpulse.entity.AppUser;
import com.transitpulse.repository.AppUserRepository;
import com.transitpulse.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    private final AppUserRepository users; private final PasswordEncoder encoder;
    private final AuthenticationManager authenticationManager; private final JwtService jwt;
    public AuthService(AppUserRepository users, PasswordEncoder encoder, AuthenticationManager authenticationManager, JwtService jwt) { this.users=users; this.encoder=encoder; this.authenticationManager=authenticationManager; this.jwt=jwt; }
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists.");
        AppUser user = users.save(new AppUser(request.fullName().trim(), email, encoder.encode(request.password())));
        return response(user);
    }
    public AuthResponse login(LoginRequest request) {
        String email=request.email().trim().toLowerCase();
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, request.password()));
        } catch (AuthenticationException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
        }
        AppUser user=users.findByEmailIgnoreCase(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password."));
        return response(user);
    }
    public AuthResponse.UserProfile profile(String email) {
        AppUser user=users.findByEmailIgnoreCase(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account no longer exists."));
        return new AuthResponse.UserProfile(user.getId(), user.getFullName(), user.getEmail());
    }
    private AuthResponse response(AppUser user) {
        return new AuthResponse(jwt.generateToken(user.getEmail()), "Bearer", jwt.expiresAt(), new AuthResponse.UserProfile(user.getId(), user.getFullName(), user.getEmail()));
    }
}
