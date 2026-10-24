package com.qr_ordering_system.app.tables.web;

import com.qr_ordering_system.app.tables.dto.response.PublicTableMenuResponse;
import com.qr_ordering_system.app.tables.service.PublicTableMenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The public customer endpoints (Phase 3).
 *
 * <p>These URLs are BAKED INTO PRINTED QR CODES ({@code /t/{slug}} on the
 * frontend). They must never change shape once table stickers exist, and
 * they must never grow anything but GET-reading endpoints without a very
 * good reason - a customer has no login, so anything beyond reads needs
 * its own protection (Phase 4 idempotent order submission).</p>
 *
 * <p>No @PreAuthorize anywhere: SecurityConfig permits {@code /api/public/**}
 * and the QR slug is the only credential. Tenant/branch/permission context
 * is derived from the slug alone - never from a header or request parameter,
 * which a customer would have no way to set honestly.</p>
 */
@RestController
@RequestMapping("api/public/tables")
@RequiredArgsConstructor
public class PublicTableController {

    private final PublicTableMenuService publicTableMenuService;

    /**
     * One round trip for a phone on restaurant Wi-Fi: whose menu, which
     * table, and the availability-filtered menu itself.
     *
     * 404 with a generic message for unknown slugs and closed branches -
     * the response must not reveal which tenant/branch/table a dead code
     * belonged to.
     */
    @GetMapping("/{qrSlug}/menu")
    public ResponseEntity<PublicTableMenuResponse> menu(@PathVariable String qrSlug) {
        return ResponseEntity.ok(publicTableMenuService.menuForSlug(qrSlug));
    }
}
