package com.transitpulse.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;

@Service
public class JwtService {
    private final SecretKey key;
    private final long expirationMinutes;
    public JwtService(@Value("${transitpulse.jwt.secret}") String secret,
                      @Value("${transitpulse.jwt.expiration-minutes:480}") long expirationMinutes) {
        byte[] decoded = Decoders.BASE64.decode(secret);
        if (decoded.length < 32) throw new IllegalArgumentException("JWT_SECRET must be Base64-encoded and contain at least 32 random bytes");
        this.key = Keys.hmacShaKeyFor(decoded); this.expirationMinutes = expirationMinutes;
    }
    public String generateToken(String email) {
        Instant now = Instant.now();
        return Jwts.builder().subject(email).issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(expirationMinutes * 60)))
            .signWith(key).compact();
    }
    public String extractSubject(String token) { return claims(token).getSubject(); }
    public boolean isValid(String token, String expectedSubject) {
        try { return expectedSubject.equals(claims(token).getSubject()) && claims(token).getExpiration().after(new Date()); }
        catch (RuntimeException ex) { return false; }
    }
    public LocalDateTime expiresAt() { return LocalDateTime.ofInstant(Instant.now().plusSeconds(expirationMinutes * 60), ZoneOffset.UTC); }
    private Claims claims(String token) { return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload(); }
}
