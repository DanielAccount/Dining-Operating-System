package com.qr_ordering_system.app.common.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.TenantId;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;

/**
 * Base for immutable audit-style rows: {@code order_item_modifiers},
 * {@code order_status_history} (and anything later that must never be
 * edited or soft-deleted).
 *
 * <p>Deliberately slimmer than {@link BaseEntity}: no {@code @Version}
 * (nothing should ever update these rows), no {@code @SoftDelete} (they
 * must survive forever, and the V5 tables have no {@code is_deleted}
 * column), no {@code updated_at}. Creating happens once; reading happens
 * forever; changing is a bug.</p>
 *
 * <p>{@code @TenantId} is kept, so Hibernate both filters reads by the
 * current tenant and populates {@code tenant_id} on insert - the seeder is
 * still the only place allowed to set it by hand.</p>
 *
 * <p>{@code createdBy} is nullable on purpose: rows created by anonymous
 * QR customers have no staff user to credit, and {@code JpaAuditingConfig}'s
 * auditor returns empty for them.</p>
 *
 * <p>Note what this class does NOT bring: the optimistic-lock annotation
 * would require a {@code version} column that the audit tables do not
 * have - which is also why this does not extend {@link BaseEntity}.</p>
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
public abstract class AbstractTenantAuditableEntity extends AbstractIdentifiable {

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private OffsetDateTime createdAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private Long createdBy;
}
