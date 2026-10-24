package com.qr_ordering_system.app.common.security;

import com.qr_ordering_system.app.common.tenant.BranchContextHolder;
import com.qr_ordering_system.app.common.tenant.TenantContextHolder;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Tenant-less paths: the login endpoint and the public storefront
        // (QR scans) must run with NO tenant pinned, whoever calls them.
        //
        // The bug this guards against is real and was caught by the Phase 3
        // smoke: a login request that carries another user's still-valid
        // Bearer token used to open its transaction with THAT tenant pinned,
        // so logging in as a different tenant's user failed (or worse, served
        // the wrong tenant's rows) — the native email lookup is deliberately
        // cross-tenant, but everything after it filters by the session's
        // tenant. Same hazard for /api/public/**: a customer with a staff
        // session in the same browser must not have that tenant leak into a
        // table-slug lookup from another restaurant.
        //
        // The token is still parsed (harmless), but no thread-locals are set.
        String path = request.getRequestURI();
        boolean tenantlessPath =
                path.equals("/api/auth/login")
                || path.startsWith("/api/public/");

        try {
            String authorizationHeader = request.getHeader("Authorization");

            if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
                String token = authorizationHeader.substring(7);

                try {
                    Claims claims = jwtService.parseToken(token);

                    Long userId = Long.valueOf(claims.getSubject());
                    Long tenantId = getLongClaim(claims, "tenantId");
                    Long branchId = getLongClaim(claims, "branchId");
                    String email = claims.get("email", String.class);
                    String username = claims.get("username", String.class);
                    String roleName = claims.get("role", String.class);

                    Set<String> permissions = new HashSet<>();
                    Object permissionsClaim = claims.get("permissions");

                    if (permissionsClaim instanceof List<?> permissionList) {
                        for (Object permission : permissionList) {
                            permissions.add(String.valueOf(permission));
                        }
                    }

                    SecurityUser securityUser = new SecurityUser(
                            userId,
                            tenantId,
                            branchId,
                            email,
                            username,
                            roleName,
                            Set.copyOf(permissions)
                    );

                    List<SimpleGrantedAuthority> authorities = new ArrayList<>();

                    if (roleName != null && !roleName.isBlank()) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + roleName));
                    }

                    permissions.forEach(permission ->
                            authorities.add(new SimpleGrantedAuthority(permission))
                    );

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    securityUser,
                                    null,
                                    authorities
                            );

                    authentication.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                    );

                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    if (tenantId != null && !tenantlessPath) {
                        TenantContextHolder.setTenantId(tenantId);
                    }

                    if (branchId != null && !tenantlessPath) {
                        BranchContextHolder.setBranchId(branchId);
                    }

                } catch (Exception exception) {
                    // A bad token is a normal event (expiry, tampering, a stale
                    // session), not an error - the request simply continues
                    // unauthenticated and the authorization rules answer 401.
                    // It was previously swallowed completely, which made every
                    // "my token is being rejected" report undiagnosable, so it is
                    // logged at DEBUG for the expected expiry case and at WARN
                    // with the cause for anything else.
                    if (exception instanceof io.jsonwebtoken.ExpiredJwtException) {
                        log.debug("Rejected expired bearer token");
                    } else {
                        log.warn("Rejected bearer token: {}", exception.getMessage());
                    }
                    SecurityContextHolder.clearContext();
                    TenantContextHolder.clear();
                    BranchContextHolder.clear();
                }
            }

            filterChain.doFilter(request, response);

        } finally {
            SecurityContextHolder.clearContext();
            TenantContextHolder.clear();
            BranchContextHolder.clear();
        }
    }

    private Long getLongClaim(Claims claims, String name) {
        Object value = claims.get(name);

        if (value == null) {
            return null;
        }

        if (value instanceof Number number) {
            return number.longValue();
        }

        return Long.valueOf(value.toString());
    }

}
