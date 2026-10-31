package com.qr_ordering_system.app.orders.service;

import com.qr_ordering_system.app.tables.repository.TableSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Mints the short codes a customer's phone keeps to re-attach to a table
 * session ({@code uk_table_session_code} makes them unique per table).
 *
 * <p>Same reasoning as {@code QrSlugGenerator}, applied to a different
 * resource: random rather than derived, so nobody can enumerate other
 * tables' session codes; 12 base62 chars, so collisions are lottery-grade
 * and codes stay comfortable in localStorage and URLs alike.</p>
 */
@Component
@RequiredArgsConstructor
public class SessionCodeGenerator {

    private static final String ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private static final int CODE_LENGTH = 12;
    private static final int MAX_ATTEMPTS = 10;

    private final TableSessionRepository tableSessionRepository;

    /** Initialized inline, so Lombok leaves it out of the constructor. */
    private final SecureRandom random = new SecureRandom();

    /** A fresh code for the given table, verified against existing sessions. */
    public String generateFor(Long tableId) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            String candidate = randomCode();
            if (tableSessionRepository.findByTableIdAndSessionCode(tableId, candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "Failed to generate a unique session code after " + MAX_ATTEMPTS + " attempts");
    }

    private String randomCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
