package com.qr_ordering_system.app.identity.service;

import com.qr_ordering_system.app.common.tenant.domain.Branch;
import com.qr_ordering_system.app.common.tenant.domain.Tenant;
import com.qr_ordering_system.app.identity.domain.Permission;
import com.qr_ordering_system.app.identity.domain.Role;
import com.qr_ordering_system.app.identity.domain.User;
import com.qr_ordering_system.app.identity.dto.MeResponse;
import com.qr_ordering_system.app.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class MeResponseService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public MeResponse byUserId(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(()-> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "UNAUTHORIZED"
                ));
        return from(user);
    }

    @Transactional(readOnly = true)
    public MeResponse from(User user) {

        Hibernate.initialize(user.getTenant());
        Tenant tenant = user.getTenant();

        MeResponse.TenantDto tenantDto = new MeResponse.TenantDto(
                tenant.getId(),
                tenant.getBusinessName(),
                tenant.getAddress(),
                tenant.getStatus() == null ? null : tenant.getStatus().name()
        );

        MeResponse.BranchDto branchDto = null;

        if (user.getBranch() != null) {
            Hibernate.initialize(user.getBranch());
            Branch branch = user.getBranch();

            branchDto = new MeResponse.BranchDto(
                    branch.getId(),
                    branch.getName(),
                    branch.getStatus() == null ? null : branch.getStatus().name()
            );
        }

        Hibernate.initialize(user.getRole());
        Role role = user.getRole();

        Hibernate.initialize(role.getPermissions());

        Set<String> permissions = role.getPermissions()
                .stream()
                .map(Permission::getCode)
                .collect(Collectors.toCollection(TreeSet::new));

        return new MeResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getDisplayName(),
                user.getPreferredLanguage(),
                Boolean.TRUE.equals(user.getIsActive()),
                tenantDto,
                branchDto,
                role.getName(),
                permissions
        );
    }


}
