package com.qr_ordering_system.app.common.security;

import java.util.Set;

//This is the authenticated principal stored in Spring Security.

public record SecurityUser(
        Long userId,
        Long tenantId,
        Long branchId,
        String email,
        String username,
        String roleName,
        Set<String> permissions
) { }

