package com.qr_ordering_system.app.identity.service;

import com.qr_ordering_system.app.common.domain.TenantStatus;
import com.qr_ordering_system.app.common.security.JwtService;
import com.qr_ordering_system.app.common.tenant.TenantContextHolder;
import com.qr_ordering_system.app.common.tenant.domain.Tenant;
import com.qr_ordering_system.app.common.tenant.repository.TenantRepository;

import com.qr_ordering_system.app.identity.domain.Permission;
import com.qr_ordering_system.app.identity.domain.Role;
import com.qr_ordering_system.app.identity.domain.User;
import com.qr_ordering_system.app.identity.dto.LoginRequest;
import com.qr_ordering_system.app.identity.dto.LoginResponse;
import com.qr_ordering_system.app.identity.dto.MeResponse;
import com.qr_ordering_system.app.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int  MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_MINUTES = 15;

    private final UserRepository userRepository;
    private final TenantRepository tenantRepostory;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final MeResponseService meResponseService;

    /*
     * Whoever sets a ThreadLocal owns clearing it. The tenant context set during
     * login is cleared here so pooled request threads never leak tenant state,
     * even if this code path is ever reached without going through the JWT filter.
     */
    // noRollbackFor allows failed-login attempts to be saved even though we throw a login exception.
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public LoginResponse login(LoginRequest request){
        try {
            return doLogin(request);
        } finally {
            TenantContextHolder.clear();
        }
    }

    private LoginResponse doLogin(LoginRequest request){

        String email = request.email() == null
                ? ""
                : request.email().trim().toLowerCase(Locale.ROOT);

        User user = userRepository.findByEmailForLogin(email)
                .orElseThrow(()-> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Invalid email or password"
                ));

        TenantContextHolder.setTenantId(user.getTenantId());

        OffsetDateTime now = OffsetDateTime.now();

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid email or password"
            );
        }

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid email or password"
            );
        }

        Tenant tenant = tenantRepostory.findById(user.getTenantId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Invalid email or password"
                ));

        if (tenant.getStatus() != TenantStatus.ACTIVE &&
                tenant.getStatus() != TenantStatus.TRIAL) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid email or password"
            );
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            handleFailedLogin(user, now);

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid email or password"
            );
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);
        userRepository.save(user);

        Hibernate.initialize(user.getRole());
        Role role = user.getRole();

        Hibernate.initialize(role.getPermissions());

        Set<String> permissions = role.getPermissions()
                .stream()
                .map(Permission::getCode)
                .collect(Collectors.toCollection(TreeSet::new));

        Long branchId = null;

        if(user.getBranch() != null) {
            Hibernate.initialize(user.getBranch());
            branchId = user.getBranch().getId();
        }

        String token  = jwtService.generateToken(
                user.getId(),
                user.getTenantId(),
                branchId,
                user.getEmail(),
                user.getUsername(),
                role.getName(),
                permissions
        );

        MeResponse meResponse = meResponseService.from(user);

        return new LoginResponse(
                token,
                jwtService.getExpirationMs(),
                meResponse
        );

    }

    private void handleFailedLogin(User user, OffsetDateTime now) {
        int failedAttempts = user.getFailedLoginAttempts() == null
                ? 0
                : user.getFailedLoginAttempts();

        failedAttempts++;
        user.setFailedLoginAttempts(failedAttempts);

        if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
            user.setLockedUntil(now.plusMinutes(LOCK_MINUTES));
        }

        userRepository.save(user);
    }

}
