package com.qr_ordering_system.app.tables.service;

import com.qr_ordering_system.app.tables.repository.TableRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the slug minter - pure logic, no database.
 *
 * What is deliberately NOT here: persistence, tenant filtering, and the
 * DB's global unique index. Those belong to the Phase 3 smoke script,
 * which exercises the real endpoints against the real database.
 */
@ExtendWith(MockitoExtension.class)
class QrSlugGeneratorTest {

    @Mock
    private TableRepository tableRepository;

    @Test
    @DisplayName("generated slugs are 10 chars from the base62 alphabet")
    void slugFormatMatchesThePrintedQrContract() {
        QrSlugGenerator generator = new QrSlugGenerator(tableRepository);
        when(tableRepository.existsByQrSlug(anyString())).thenReturn(false);

        String slug = generator.generateUnique();

        assertEquals(10, slug.length());
        assertTrue(slug.matches("[A-Za-z0-9]+"));
    }

    @Test
    @DisplayName("two slugs from the same generator are different")
    void consecutiveSlugsAreRandom() {
        QrSlugGenerator generator = new QrSlugGenerator(tableRepository);
        when(tableRepository.existsByQrSlug(anyString())).thenReturn(false);

        // 10 random chars: identical consecutive draws are lottery-grade,
        // so this asserting inequality is a real test, not a flaky one.
        assertFalse(generator.generateUnique().equals(generator.generateUnique()));
    }

    @Test
    @DisplayName("a collision with an existing slug is retried, not returned")
    void collisionWithExistingSlugIsRetried() {
        QrSlugGenerator generator = new QrSlugGenerator(tableRepository);
        when(tableRepository.existsByQrSlug(anyString()))
                .thenReturn(true)   // first draw collides
                .thenReturn(false); // second draw is free

        generator.generateUnique();

        verify(tableRepository, times(2)).existsByQrSlug(anyString());
    }

    @Test
    @DisplayName("exhausted attempts fail loudly instead of inserting a duplicate")
    void exhaustedAttemptsThrow() {
        QrSlugGenerator generator = new QrSlugGenerator(tableRepository);
        when(tableRepository.existsByQrSlug(anyString())).thenReturn(true);

        assertThrows(IllegalStateException.class, generator::generateUnique);
        // Every attempt actually asked the repository - no silent early exit.
        verify(tableRepository, times(10)).existsByQrSlug(anyString());
    }
}
