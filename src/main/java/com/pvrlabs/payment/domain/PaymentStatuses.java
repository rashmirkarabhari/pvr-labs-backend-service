package com.pvrlabs.payment.domain;

/**
 * Canonical payment / order status values used by this service.
 * Payment attempts from Cashfree are mapped onto {@link #PENDING}, {@link #SUCCESS}, {@link #FAILED}.
 * After a verified successful payment, the business order is {@link #CONFIRMED}.
 */
public final class PaymentStatuses {

    public static final String PENDING = "PENDING";
    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";
    public static final String CONFIRMED = "CONFIRMED";

    /** Cashfree order status when the order has been paid. */
    public static final String CASHFREE_PAID = "PAID";
    public static final String CASHFREE_ACTIVE = "ACTIVE";
    public static final String CASHFREE_EXPIRED = "EXPIRED";

    private PaymentStatuses() {
    }
}
