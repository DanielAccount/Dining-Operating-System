package com.qr_ordering_system.app.tables.domain;

import com.qr_ordering_system.app.common.domain.SessionState;
import com.qr_ordering_system.app.common.persistence.AbstractTenantEntity;
import com.qr_ordering_system.app.common.tenant.domain.Branch;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * One dining session at one table (Phase 4 anchor).
 *
 * <p>Lifecycle, per the Phase 4 plan: a session opens LAZILY - only when a
 * customer submits the table's first order, not when someone merely scans
 * the QR - then runs {@code CUSTOMER_ORDERING} until staff mark the table
 * {@code AWAITING_CLEANUP} (Phase 6 waiter tooling) and finally
 * {@code CLOSED} (table reset, frees the table for the next party).</p>
 *
 * <p>The database enforces the invariants through two partial unique
 * indexes from V4: one OPEN session per table at a time
 * ({@code uk_table_session_open WHERE session_state <> 'CLOSED'}) and
 * {@code uk_table_session_code (table_id, session_code)} - the code being
 * what a customer's phone keeps in localStorage to re-find "its" table's
 * orders on the next scan.</p>
 *
 * <p>Lives in the tables module (not orders) because the tables screen
 * itself will need "is this table occupied?" answers from these rows.</p>
 */
@Entity
@Table(name = "table_sessions", indexes = {
        @Index(name = "idx_table_session_tenant", columnList = "tenant_id"),
        @Index(name = "idx_table_session_table", columnList = "table_id"),
        @Index(name = "idx_table_session_state", columnList = "tenant_id, session_state")
})
@Getter @Setter
public class TableSession extends AbstractTenantEntity {

    /**
     * EAGER is required, not a choice: Hibernate 7 refuses LAZY to-one
     * associations to @SoftDelete entities. See the same comment on
     * BranchMenuItem.branch for the full story.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @ManyToOne(fetch = FetchType.EAGER) // see the comment on `branch`
    @JoinColumn(name = "table_id", nullable = false)
    private TableEntity table;

    /**
     * Given to the customer's phone on first submission; the phone stores
     * it and re-attaches with it on later scans of the same table.
     */
    @Column(name = "session_code", nullable = false, length = 64)
    private String sessionCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "session_state", nullable = false, columnDefinition = "varchar(32) default 'CUSTOMER_ORDERING'")
    private SessionState sessionState = SessionState.CUSTOMER_ORDERING;

    @Column(name = "opened_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime openedAt;

    @Column(name = "closed_at", columnDefinition = "timestamptz")
    private OffsetDateTime closedAt;

    /** A session that has not been closed yet - one per table, by index. */
    public boolean isOpen() {
        return sessionState != SessionState.CLOSED;
    }

    /** A session a customer may still submit orders into. */
    public boolean isOrderable() {
        return sessionState == SessionState.CUSTOMER_ORDERING;
    }
}
