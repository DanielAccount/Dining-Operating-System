package com.qr_ordering_system.app.common.domain;

public enum PaymentStatus {
    PENDING,
    AWAITING_VERIFICATION,
    PAID,
    FAILED,
    CANCELLED,
    EXPIRED,
    PARTIALLY_REFUNDED,
    REFUNDED
}
