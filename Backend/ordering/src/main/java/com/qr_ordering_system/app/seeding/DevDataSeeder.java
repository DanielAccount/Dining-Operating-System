package com.qr_ordering_system.app.seeding;

import com.qr_ordering_system.app.common.domain.BranchStatus;
import com.qr_ordering_system.app.common.domain.TenantStatus;
import com.qr_ordering_system.app.common.tenant.TenantContextHolder;
import com.qr_ordering_system.app.common.tenant.domain.Branch;
import com.qr_ordering_system.app.common.tenant.domain.Tenant;
import com.qr_ordering_system.app.common.tenant.repository.BranchRepository;
import com.qr_ordering_system.app.common.tenant.repository.TenantRepository;
import com.qr_ordering_system.app.identity.domain.Permission;
import com.qr_ordering_system.app.identity.domain.Role;
import com.qr_ordering_system.app.identity.domain.User;
import com.qr_ordering_system.app.identity.repository.PermissionRepository;
import com.qr_ordering_system.app.identity.repository.RoleRepository;
import com.qr_ordering_system.app.identity.repository.UserRepository;
import com.qr_ordering_system.app.tables.domain.TableEntity;
import com.qr_ordering_system.app.tables.repository.TableRepository;
import com.qr_ordering_system.app.tables.service.QrSlugGenerator;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Profile("local")
@RequiredArgsConstructor
public class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private static final String DEV_PASSWORD = "Password123!";

    private final PermissionRepository permissionRepository;
    private final TenantRepository tenantRepository;
    private final BranchRepository branchRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TableRepository tableRepository;
    private final QrSlugGenerator qrSlugGenerator;

    /** Where the storefront lives; QR urls are logged per seeded table. */
    @Value("${app.public.base-url}")
    private String publicBaseUrl;

    // ---------------------------------------------------------
    // The global permission catalog (never changes per tenant)
    // ---------------------------------------------------------
    private static final List<String> ALL_PERMISSIONS = List.of(
            "DASHBOARD_VIEW",
            "ORDER_VIEW", "ORDER_CREATE", "ORDER_UPDATE", "ORDER_CANCEL",
            "KDS_VIEW", "KDS_UPDATE_ITEM_STATUS",
            "PAYMENT_VIEW", "PAYMENT_VERIFY", "PAYMENT_COLLECT_CASH", "PAYMENT_REFUND",
            "TABLE_VIEW", "TABLE_MANAGE",
            "SERVICE_REQUEST_VIEW", "SERVICE_REQUEST_HANDLE",
            "MENU_VIEW", "MENU_MANAGE",
            "BRANCH_VIEW", "BRANCH_MANAGE",
            "USER_VIEW", "USER_MANAGE",
            "REPORT_VIEW",
            "SETTINGS_VIEW", "SETTINGS_MANAGE",
            "SUBSCRIPTION_VIEW", "SUBSCRIPTION_MANAGE"
    );

    // Role name -> permission codes (the same template used for EVERY tenant)
    private static final Map<String, List<String>> DEFAULT_ROLES = new LinkedHashMap<>() {{
        put("OWNER", List.of(
                "DASHBOARD_VIEW",
                "ORDER_VIEW", "ORDER_CREATE", "ORDER_UPDATE", "ORDER_CANCEL",
                "KDS_VIEW", "KDS_UPDATE_ITEM_STATUS",
                "PAYMENT_VIEW", "PAYMENT_VERIFY", "PAYMENT_COLLECT_CASH", "PAYMENT_REFUND",
                "TABLE_VIEW", "TABLE_MANAGE",
                "SERVICE_REQUEST_VIEW",
                "MENU_VIEW", "MENU_MANAGE",
                "BRANCH_VIEW", "BRANCH_MANAGE",
                "USER_VIEW", "USER_MANAGE",
                "REPORT_VIEW",
                "SETTINGS_VIEW", "SETTINGS_MANAGE",
                "SUBSCRIPTION_VIEW", "SUBSCRIPTION_MANAGE"));
        put("MANAGER", List.of(
                "DASHBOARD_VIEW",
                "ORDER_VIEW", "ORDER_CREATE", "ORDER_UPDATE", "ORDER_CANCEL",
                "KDS_VIEW", "KDS_UPDATE_ITEM_STATUS",
                "PAYMENT_VIEW", "PAYMENT_VERIFY", "PAYMENT_COLLECT_CASH",
                "TABLE_VIEW", "TABLE_MANAGE",
                "SERVICE_REQUEST_VIEW", "SERVICE_REQUEST_HANDLE",
                "MENU_VIEW", "MENU_MANAGE",
                "BRANCH_VIEW",
                "USER_VIEW",
                "REPORT_VIEW",
                "SETTINGS_VIEW"));
        put("CASHIER", List.of(
                "ORDER_VIEW", "ORDER_CREATE", "ORDER_CANCEL",
                "PAYMENT_VIEW", "PAYMENT_VERIFY", "PAYMENT_COLLECT_CASH",
                "TABLE_VIEW"));
        put("WAITER", List.of(
                "ORDER_VIEW", "ORDER_CREATE", "ORDER_UPDATE",
                "PAYMENT_VIEW", "PAYMENT_COLLECT_CASH",
                "TABLE_VIEW",
                "SERVICE_REQUEST_VIEW", "SERVICE_REQUEST_HANDLE",
                "MENU_VIEW"));
        put("CHEF", List.of(
                "ORDER_VIEW",
                "KDS_VIEW", "KDS_UPDATE_ITEM_STATUS",
                "MENU_VIEW"));
    }};

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Map<String, Permission> permissionMap = seedPermissions();

        if (tenantRepository.count() > 0) {
            log.info("Seed data already present, skipping.");
            return;
        }

        seedBrewCafe(permissionMap);
        seedEmberRestaurant(permissionMap);

        log.info("Dev seeding complete.");
    }

    // ---------------------------------------------------------
    // Permissions: global catalog, idempotent by code
    // ---------------------------------------------------------
    private Map<String, Permission> seedPermissions() {
        return ALL_PERMISSIONS.stream().collect(Collectors.toMap(
                code -> code,
                code -> permissionRepository.findByCode(code).orElseGet(() -> {
                    Permission p = new Permission();
                    p.setCode(code);
                    p.setDescription(code.replace("_", " ").toLowerCase());
                    return permissionRepository.save(p);
                })
        ));
    }

    // ---------------------------------------------------------
    // Tenant 1: Brew Cafe — single branch, owner wears all hats
    // ---------------------------------------------------------
    private void seedBrewCafe(Map<String, Permission> permissionMap) {
        Tenant tenant = new Tenant();
        tenant.setBusinessName("Brew Cafe");
        tenant.setContactNumber("09000000001");
        tenant.setAddress("Yangon");
        tenant.setTimezone("Asia/Yangon");
        tenant.setStatus(TenantStatus.ACTIVE);
        tenantRepository.save(tenant);

        Long tenantId = tenant.getId();
        TenantContextHolder.setTenantId(tenantId);
        try {
            Map<String, Role> roles = seedRoles(tenantId, permissionMap);

            Branch branch = new Branch();
            branch.setTenantId(tenantId); 
            branch.setName("Brew Cafe Main");
            branch.setAddress("Yangon");
            branch.setContactNumber("09000000001");
            branch.setTimezone("Asia/Yangon");
            branch.setStatus(BranchStatus.ACTIVE);
            branchRepository.save(branch);

            // FIXED: Removed line breaks inside string literals
            seedUser(tenantId, branch, roles.get("OWNER"), "brew.owner@example.com", "brew.owner", "U Aung (Owner)");
            seedUser(tenantId, branch, roles.get("WAITER"), "brew.waiter1@example.com", "brew.waiter1", "Kyaw Kyaw (Waiter)");
            seedUser(tenantId, branch, roles.get("WAITER"), "brew.waiter2@example.com", "brew.waiter2", "Hla Hla (Waiter)");
            seedUser(tenantId, branch, roles.get("WAITER"), "brew.waiter3@example.com", "brew.waiter3", "Min Min (Waiter)");
            seedUser(tenantId, branch, roles.get("CHEF"), "brew.chef@example.com", "brew.chef", "Chef Ko Ko");

            seedTables(tenantId, branch, "T1", "T2", "T3", "T4");
        } finally {
            TenantContextHolder.clear();
        }
    }

    // ---------------------------------------------------------
    // Tenant 2: Ember Restaurant — Mandalay + Yangon branches
    // ---------------------------------------------------------
    private void seedEmberRestaurant(Map<String, Permission> permissionMap) {
        Tenant tenant = new Tenant();
        tenant.setBusinessName("Ember Restaurant");
        tenant.setContactNumber("09000000002");
        tenant.setAddress("Mandalay");
        tenant.setTimezone("Asia/Yangon");
        tenant.setStatus(TenantStatus.ACTIVE);
        tenantRepository.save(tenant);

        Long tenantId = tenant.getId();
        TenantContextHolder.setTenantId(tenantId);
        try {
            Map<String, Role> roles = seedRoles(tenantId, permissionMap);

            Branch mandalay = createBranch(tenantId, "Ember Mandalay", "Mandalay", "09000000002");
            Branch yangon = createBranch(tenantId, "Ember Yangon", "Yangon", "09000000003");

            // Owner has branch = null => tenant-wide access (can see BOTH branches)
            seedUser(tenantId, null, roles.get("OWNER"), "ember.owner@example.com", "ember.owner", "U Zaw (Owner)");

            seedUser(tenantId, mandalay, roles.get("MANAGER"), "ember.mdy.manager@example.com", "ember.mdy.manager", "Thida (MDY Manager)");
            seedUser(tenantId, yangon, roles.get("MANAGER"), "ember.ygn.manager@example.com", "ember.ygn.manager", "Mya (YGN Manager)");

            seedUser(tenantId, mandalay, roles.get("CASHIER"), "ember.mdy.cashier@example.com", "ember.mdy.cashier", "Nwe (MDY Cashier)");
            seedUser(tenantId, yangon, roles.get("CASHIER"), "ember.ygn.cashier@example.com", "ember.ygn.cashier", "Ei (YGN Cashier)");

            seedUser(tenantId, mandalay, roles.get("WAITER"), "ember.mdy.waiter@example.com", "ember.mdy.waiter", "Aye (MDY Waiter)");
            seedUser(tenantId, yangon, roles.get("WAITER"), "ember.ygn.waiter@example.com", "ember.ygn.waiter", "Thiri (YGN Waiter)");

            seedUser(tenantId, mandalay, roles.get("CHEF"), "ember.mdy.chef@example.com", "ember.mdy.chef", "Chef Htun (MDY)");
            seedUser(tenantId, yangon, roles.get("CHEF"), "ember.ygn.chef@example.com", "ember.ygn.chef", "Chef Lin (YGN)");

            seedTables(tenantId, mandalay, "T1", "T2", "T3", "T4");
            seedTables(tenantId, yangon, "T1", "T2", "T3", "T4");
        } finally {
            TenantContextHolder.clear();
        }
    }

    // ---------------------------------------------------------
    // Phase 3: a few tables per branch, so a fresh dev database
    // has scannable QR codes out of the box.
    //
    // Slugs are minted through the real QrSlugGenerator bean - seeded
    // rows obey the same format (10 base62 chars) and uniqueness
    // contract as production ones. Both branches number their tables
    // from T1 because numbers are unique per branch, like real
    // restaurants that each name their own tables.
    //
    // The seeder hand-sets tenantId on EVERY tenant-scoped entity
    // (Branch, Role, User... and TableEntity). Reason: run() opens ONE
    // transaction for the whole seed, so Hibernate pins the session's
    // tenant identifier at open time - root (0) - and an entity whose
    // @TenantId property is still null at persist binds tenant_id = 0
    // and dies on the tenants FK at flush. Hand-set values are used
    // as-is, which is exactly why the base-class comment calls the
    // seeder "the only exception".
    // ---------------------------------------------------------
    private void seedTables(Long tenantId, Branch branch, String... tableNumbers) {
        for (String number : tableNumbers) {
            TableEntity table = new TableEntity();
            table.setTenantId(tenantId);
            table.setBranch(branch);
            table.setTableNumber(number);
            table.setQrSlug(qrSlugGenerator.generateUnique());
            tableRepository.save(table);

            log.info("  seeded table '{}' for branch '{}' -> scan {} to see its menu",
                    number, branch.getName(), publicBaseUrl + "/t/" + table.getQrSlug());
        }
    }

    private Branch createBranch(Long tenantId, String name, String address, String phone) {
        Branch b = new Branch();
        b.setTenantId(tenantId); 
        b.setName(name);
        b.setAddress(address);
        b.setContactNumber(phone);
        b.setTimezone("Asia/Yangon");
        b.setStatus(BranchStatus.ACTIVE);
        return branchRepository.save(b);
    }

    // One set of roles per tenant, from the shared template
    private Map<String, Role> seedRoles(Long tenantId, Map<String, Permission> permissionMap) {
        Map<String, Role> result = new LinkedHashMap<>();
        DEFAULT_ROLES.forEach((roleName, codes) -> {
            Role role = new Role();
            role.setTenantId(tenantId); 
            role.setName(roleName);
            role.setDescription(roleName + " default role");
            role.setPermissions(codes.stream().map(permissionMap::get).collect(Collectors.toSet()));
            result.put(roleName, roleRepository.save(role));
        });
        return result;
    }

    private void seedUser(Long tenantId, Branch branch, Role role, String email, String username, String displayName) {
        User user = new User();
        user.setTenantId(tenantId); 
        user.setBranch(branch);     // null => tenant-wide
        user.setRole(role);
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(DEV_PASSWORD));

        // IMPORTANT: If you haven't added these 3 fields to your User.java entity yet,
        // comment these 3 lines out to avoid "Cannot resolve method" errors.
        user.setDisplayName(displayName);
        user.setIsActive(true);
        user.setPreferredLanguage("ENGLISH");

        user.setFailedLoginAttempts(0);
        userRepository.save(user);
    }
}