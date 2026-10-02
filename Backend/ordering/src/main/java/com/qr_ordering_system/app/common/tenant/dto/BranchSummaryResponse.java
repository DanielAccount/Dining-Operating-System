package com.qr_ordering_system.app.common.tenant.dto;

public record BranchSummaryResponse(
        Long id,
        String name,
        String status
) {}
