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

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

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

                    if (tenantId != null) {
                        TenantContextHolder.setTenantId(tenantId);
                    }

                    if (branchId != null) {
                        BranchContextHolder.setBranchId(branchId);
                    }

                } catch (Exception exception) {
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
