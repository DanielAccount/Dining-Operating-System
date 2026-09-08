package com.qr_ordering_system.app.common.tenant;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class HibernateTenantIdentifierResolver implements CurrentTenantIdentifierResolver<Long>, HibernatePropertiesCustomizer {

    // A sentinel value representing "no tenant" / "system level".
    // Ensure this ID (0L) is never used for a real tenant in your database.
    public static final Long SYSTEM_TENANT_ID = 0L;

    @Override
    public Long resolveCurrentTenantIdentifier() {
        Long tenantId = TenantContextHolder.getTenantId();

        // FIX 1 & 3: Return Long (matching @TenantId) and NEVER return null.
        // If no tenant is authenticated (e.g., during startup or login), return the system ID.
        return tenantId != null ? tenantId : SYSTEM_TENANT_ID;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true; // Recommended for proper Spring Data JPA transaction management
    }

    /**
     * FIX 2: This tells Hibernate to skip the tenant filter for the SYSTEM_TENANT_ID.
     * This allows your native login query to search across ALL tenants globally
     * without appending "AND tenant_id = 0" to the SQL.
     */
    @Override
    public boolean isRoot(Long tenantIdentifier) {
        return SYSTEM_TENANT_ID.equals(tenantIdentifier);
    }

    /**
     * FIX 4 (CRITICAL): Explicitly register this resolver with Hibernate.
     */
    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, this);
    }
}