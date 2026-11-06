package com.qr_ordering_system.app.kitchen.dto.response;

/**
 * A selected modifier as the kitchen needs it: the instruction that was
 * captured when the order was placed. Prices are deliberately omitted - a
 * KDS is a production board, not a receipt.
 */
public record KitchenModifierResponse(
        Long id,
        String name
) {
}
