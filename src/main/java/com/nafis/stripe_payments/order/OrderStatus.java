package com.nafis.stripe_payments.order;

public enum OrderStatus {
    PENDING,
    PAID,
    FAILED,
    PARTIALLY_REFUNDED,
    REFUNDED,
    DISPUTED,
    CANCELLED
}