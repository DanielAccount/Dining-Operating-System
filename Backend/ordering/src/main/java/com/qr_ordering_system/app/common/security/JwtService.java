package com.qr_ordering_system.app.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Set;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(
            @Value("${app.security.jwt.secret}") String secret,
            @Value("${app.security.jwt.expiration-ms}") long expirationMs
    ){
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public long getExpirationMs(){
        return expirationMs;
    }

    /**
     * Mints a token carrying the user's full permission set.
     *
     * KNOWN LIMITATION (accepted for MVP, revisit before Phase 7): permissions
     * and role are baked into the token, so revoking a permission or changing a
     * role has no effect until the token expires (app.security.jwt.expiration-ms,
     * currently 1 hour). There is no refresh token, no version counter on the
     * user and no revocation list. Until one of those exists, "I removed
     * MENU_MANAGE from this user" is only true after they happen to refresh -
     * the UI will hide the button while the API would still honour it.
     *
     * Fixing it properly means a token version (jti or a users.token_version
     * column checked on every request) so a stale token can be rejected
     * immediately.
     */
    public String generateToken(
            Long userId,
            Long tenantId,
            Long branchId,
            String email,
            String username,
            String roleName,
            Set<String> permissions
    ) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expirationMs);

        var builder = Jwts.builder()
                .subject(userId.toString())
                .claim("tenantId", tenantId)
                .claim("email", email)
                .claim("username", username)
                .claim("role", roleName)
                .claim("permissions", permissions)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(key);

        if (branchId != null) {
            builder.claim("branchId", branchId);
        }

        return builder.compact();
    }

    public Claims parseToken (String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

}
