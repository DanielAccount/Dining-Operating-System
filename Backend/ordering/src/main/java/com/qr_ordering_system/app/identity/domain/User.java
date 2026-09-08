package com.qr_ordering_system.app.identity.domain;

import com.qr_ordering_system.app.common.persistence.AbstractTenantEntity;
import com.qr_ordering_system.app.common.tenant.domain.Branch;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(
        name = "users",
        indexes = {
                @Index(name = "idx_user_tenant", columnList = "tenant_id"),
                @Index(name = "idx_user_email_lookup", columnList = "email")
        }
        // FIX: email is now the identity used for unauthenticated tenant-resolution at login,
        // so it must be GLOBALLY unique, not just per-tenant — otherwise "login by email"
        // is ambiguous when two tenants happen to have staff with the same address.
        // Enforced as a soft-delete-safe partial unique index in schema.sql.
        // username uniqueness is still scoped per-tenant -> schema.sql
)
@Getter
@Setter
public class User extends AbstractTenantEntity {

    @ManyToOne
    @JoinColumn(name = "branch_id")
    private Branch branch; // null = tenant-wide access

    @Column(nullable = false)
    private String username;

    @Column(nullable = false, columnDefinition = "citext")
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "last_login_at", columnDefinition = "timestamptz")
    private OffsetDateTime lastLoginAt;

    @Column(name = "preferred_language")
    private String preferredLanguage; // Myanamar or English

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id", nullable = false, foreignKey = @ForeignKey(name = "fk_user_role"))
    private Role role;

    @Column(name = "failed_login_attempts", nullable = false)
    private Integer failedLoginAttempts = 0;

    @Column(name = "locked_until", columnDefinition = "timestamptz")
    private OffsetDateTime lockedUntil;
}
