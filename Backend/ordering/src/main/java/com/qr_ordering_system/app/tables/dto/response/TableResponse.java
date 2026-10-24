package com.qr_ordering_system.app.tables.dto.response;

import java.time.OffsetDateTime;

/**
 * One table, as the management screens see it.
 *
 * {@code qrUrl} is the complete URL encoded inside the QR image -
 * {@code {app.public.base-url}/t/{qrSlug}} - so the frontend renders and
 * prints without ever assembling URLs itself. The base URL is deployment
 * config ({@code app.public.base-url}), which means dev codes point at the
 * dev server and production codes at the production domain, with no code
 * knowing the difference.
 */
public record TableResponse(
        Long id,
        String tableNumber,
        String qrSlug,
        String qrUrl,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
