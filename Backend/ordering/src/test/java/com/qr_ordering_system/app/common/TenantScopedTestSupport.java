package com.qr_ordering_system.app.common;

import com.qr_ordering_system.app.common.domain.TenantStatus;
import com.qr_ordering_system.app.common.tenant.TenantContextHolder;
import com.qr_ordering_system.app.common.tenant.domain.Tenant;
import com.qr_ordering_system.app.common.tenant.repository.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Base class for tests that need a real tenant and real, committed rows.
 *
 * <h2>Why this is not {@code @Transactional}</h2>
 *
 * A test-managed transaction begins <em>before</em> {@code @BeforeEach} runs, and
 * Spring opens the Hibernate session at begin time. Hibernate resolves the
 * current tenant identifier <em>once per session</em>, so a tenant set in
 * {@code @BeforeEach} arrives too late: the session is already pinned to
 * SYSTEM_TENANT_ID (0), which {@code isRoot()} treats as "no filtering at all".
 * The result is a cross-tenant read and an insert that violates the
 * {@code tenant_id} foreign key - the exact failure this harness exists to
 * prevent, so the tests would have been measuring the wrong thing.
 *
 * Instead every unit of work goes through {@link #inTenant}, which sets the
 * thread-local <em>before</em> the transaction - the same order the JWT filter
 * uses - and commits. A throwaway tenant is created per test and deleted
 * afterwards, so the suite neither depends on the dev seeder nor leaves anything
 * behind.
 *
 * <p>Lives in the shared {@code app.common} test package so any module's tests
 * can extend it (it started life inside {@code app.menu}; the tables module's
 * public-menu tests needed it too).</p>
 */
@SpringBootTest
@ActiveProfiles("local")
public abstract class TenantScopedTestSupport {

    protected static final String NAME_PREFIX = "zz-test-";

    @Autowired
    protected TransactionTemplate transactionTemplate;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected TenantRepository tenantRepository;

    private final List<Long> createdTenantIds = new ArrayList<>();

    private Long primaryTenantId;

    @BeforeEach
    void createPrimaryTenant() {
        primaryTenantId = createTenant("primary");
    }

    @AfterEach
    void dropCreatedTenants() {
        TenantContextHolder.clear();

        for (Long tenantId : createdTenantIds) {
            // Children first: these foreign keys are not cascaded.
            // Order engine (Phase 4): modifiers -> items -> orders -> sessions.
            jdbcTemplate.update("DELETE FROM order_item_modifiers WHERE tenant_id = ?", tenantId);
            jdbcTemplate.update("DELETE FROM order_status_history WHERE tenant_id = ?", tenantId);
            jdbcTemplate.update("DELETE FROM order_items WHERE tenant_id = ?", tenantId);
            jdbcTemplate.update("DELETE FROM orders WHERE tenant_id = ?", tenantId);
            jdbcTemplate.update("DELETE FROM table_sessions WHERE tenant_id = ?", tenantId);
            // Menu + tables.
            jdbcTemplate.update("DELETE FROM menu_item_modifier_groups WHERE tenant_id = ?", tenantId);
            jdbcTemplate.update("DELETE FROM branch_menu_items WHERE tenant_id = ?", tenantId);
            jdbcTemplate.update("DELETE FROM restaurant_tables WHERE tenant_id = ?", tenantId);
            jdbcTemplate.update("DELETE FROM menu_items WHERE tenant_id = ?", tenantId);
            jdbcTemplate.update("DELETE FROM modifier_options WHERE tenant_id = ?", tenantId);
            jdbcTemplate.update("DELETE FROM modifier_groups WHERE tenant_id = ?", tenantId);
            jdbcTemplate.update("DELETE FROM categories WHERE tenant_id = ?", tenantId);
            jdbcTemplate.update("DELETE FROM branches WHERE tenant_id = ?", tenantId);
            jdbcTemplate.update("DELETE FROM tenants WHERE id = ?", tenantId);
        }
        createdTenantIds.clear();
    }

    /** The throwaway tenant created for the current test. */
    protected Long tenantId() {
        return primaryTenantId;
    }

    /** Creates an extra tenant - used by the isolation tests. */
    protected Long createTenant(String suffix) {
        Tenant tenant = new Tenant();
        tenant.setBusinessName(NAME_PREFIX + suffix);
        tenant.setStatus(TenantStatus.ACTIVE);
        // Tenant is not tenant-scoped itself, so no context is needed to insert it.
        Long id = tenantRepository.save(tenant).getId();
        createdTenantIds.add(id);
        return id;
    }

    /**
     * Runs one unit of work as {@code tenantId}, in its own transaction, with
     * the thread-local set first - exactly the order a request goes through.
     */
    protected <T> T inTenant(Supplier<T> work) {
        return inTenant(primaryTenantId, work);
    }

    protected <T> T inTenant(Long tenantId, Supplier<T> work) {
        TenantContextHolder.setTenantId(tenantId);
        try {
            return transactionTemplate.execute(status -> work.get());
        } finally {
            // Whoever sets a thread-local clears it: pooled threads outlive the
            // transaction and must not carry a tenant into the next test.
            TenantContextHolder.clear();
        }
    }

    protected void inTenant(Runnable work) {
        inTenant(primaryTenantId, () -> {
            work.run();
            return null;
        });
    }

    protected Map<String, String> names(String en) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("en", en);
        return map;
    }

    protected Map<String, String> emptyMap() {
        return new LinkedHashMap<>();
    }
}
