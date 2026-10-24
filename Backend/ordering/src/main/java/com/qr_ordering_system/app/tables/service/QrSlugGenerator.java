package com.qr_ordering_system.app.tables.service;

import com.qr_ordering_system.app.tables.repository.TableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Mints the random URL-safe slugs that table QR codes encode
 * ({@code {app.public.base-url}/t/{slug}}).
 *
 * <p>Design choices, each deliberate:
 *
 * <ul>
 *   <li><b>Random, not derived</b> from tenant/branch/table names: a
 *   competitor or prankster walking the restaurant must not be able to
 *   guess other tables' codes, and the URL must not leak anyone's business
 *   name.</li>
 *
 *   <li><b>10 chars, base62</b>: 62<sup>10</sup> ≈ 8.4 × 10<sup>17</sup>
 *   candidates - collisions are lottery-grade even across every tenant the
 *   platform will ever host - while the URL stays short, because it is
 *   baked into a printed QR code and shorter strings scan more reliably
 *   from across a table.</li>
 * </ul>
 *
 * <p>Collision handling has two layers, on purpose. The in-code check is
 * {@link TableRepository#existsByQrSlug}, which is tenant-filtered when
 * called from a staff request - it catches the only realistic collision
 * class, retries within the same tenant. A cross-tenant collision falls
 * through to the DB's <em>global</em> unique index {@code uk_table_qr_slug}
 * and surfaces as a 409 through the {@code DataIntegrityViolationException}
 * handler. The index is the guarantee; this loop is the friendly UX.
 */
@Component
@RequiredArgsConstructor
public class QrSlugGenerator {

    private static final String ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private static final int SLUG_LENGTH = 10;
    private static final int MAX_ATTEMPTS = 10;

    private final TableRepository tableRepository;

    /** Initialized inline, so Lombok leaves it out of the constructor. */
    private final SecureRandom random = new SecureRandom();

    /**
     * A fresh slug, verified against existing tables before it is returned.
     * Callers hand it straight to {@code new TableEntity}; never generate
     * slugs anywhere else, or uniqueness stops being one code path.
     */
    public String generateUnique() {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            String candidate = randomSlug();
            if (!tableRepository.existsByQrSlug(candidate)) {
                return candidate;
            }
        }
        // Unreachable in practice at this collision probability - the guard
        // exists so a future regression (shorter alphabet, broken seeding)
        // fails loudly instead of inserting duplicate slugs.
        throw new IllegalStateException(
                "Failed to generate a unique QR slug after " + MAX_ATTEMPTS + " attempts");
    }

    /** Package-private so the unit test can exercise format without mocking. */
    String randomSlug() {
        StringBuilder slug = new StringBuilder(SLUG_LENGTH);
        for (int i = 0; i < SLUG_LENGTH; i++) {
            slug.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return slug.toString();
    }
}
