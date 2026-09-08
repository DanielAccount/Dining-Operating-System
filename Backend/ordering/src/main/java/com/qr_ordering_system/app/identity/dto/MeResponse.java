package com.qr_ordering_system.app.identity.dto;

import java.util.Set;

public record MeResponse(
        Long id,
        String username,
        String email,
        String displayName,
        String preferredLanguage,
        boolean active,
        TenantDto tenant,
        BranchDto branch,
        String role,
        Set<String> permissions
) {
    public record TenantDto(
            Long id,
            String businessName,
            String address,
            String status
    ) {
    }

    public record BranchDto(
            Long id,
            String name,
            String status
    ) {
    }

}
