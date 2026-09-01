package com.pvrlabs.payment.domain;

import java.util.Locale;
import java.util.Set;

/**
 * Fulfillment statuses for admin order management.
 * Payment confirmation still uses {@link PaymentStatuses}; checkout records start as PENDING.
 */
public final class OrderStatuses {

    public static final String CREATED = "CREATED";
    public static final String CONFIRMED = "CONFIRMED";
    public static final String PROCESSING = "PROCESSING";
    public static final String SHIPPED = "SHIPPED";
    public static final String DELIVERED = "DELIVERED";
    public static final String CANCELLED = "CANCELLED";

    /** Existing checkout status before/while payment is captured. */
    public static final String PENDING = PaymentStatuses.PENDING;

    private static final Set<String> SUPPORTED = Set.of(
            CREATED,
            CONFIRMED,
            PROCESSING,
            SHIPPED,
            DELIVERED,
            CANCELLED,
            PENDING
    );

    private OrderStatuses() {
    }

    public static boolean isSupported(String status) {
        if (status == null || status.isBlank()) {
            return false;
        }
        return SUPPORTED.contains(status.trim().toUpperCase(Locale.ROOT));
    }

    public static String normalize(String status) {
        return status.trim().toUpperCase(Locale.ROOT);
    }

    public static boolean isCompleted(String status) {
        return DELIVERED.equalsIgnoreCase(status);
    }

    public static boolean isOpen(String status) {
        return status != null
                && !isCompleted(status)
                && !CANCELLED.equalsIgnoreCase(status);
    }
}
