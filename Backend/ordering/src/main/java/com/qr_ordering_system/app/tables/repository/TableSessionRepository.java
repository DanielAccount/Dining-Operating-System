package com.qr_ordering_system.app.tables.repository;

import com.qr_ordering_system.app.common.domain.SessionState;
import com.qr_ordering_system.app.tables.domain.TableSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TableSessionRepository extends JpaRepository<TableSession, Long> {

    /**
     * The orderable session of a table, if one exists. The
     * {@code uk_table_session_open} partial index (one non-CLOSED session
     * per table) makes "at most one" a database guarantee, but findFirst
     * keeps the query honest against corrupted data instead of blowing up.
     */
    Optional<TableSession> findFirstByTableIdAndSessionStateOrderByIdDesc(Long tableId, SessionState sessionState);

    /**
     * The session that still "owns" the table, if one exists (CUSTOMER_ORDERING
     * or AWAITING_CLEANUP). The {@code uk_table_session_open} partial index
     * (one non-CLOSED session per table) makes "at most one" a database
     * guarantee, but findFirst keeps the query honest against corrupted data
     * instead of blowing up.
     */
    Optional<TableSession> findFirstByTableIdAndSessionStateNotOrderByIdDesc(Long tableId, SessionState state);

    /**
     * Re-attachment lookup for a returning phone: the customer's browser
     * keeps the sessionCode from a previous submission and presents it
     * after scanning the same table again. Unique per
     * {@code uk_table_session_code (table_id, session_code)}.
     */
    Optional<TableSession> findByTableIdAndSessionCode(Long tableId, String sessionCode);
}
