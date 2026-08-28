package com.nafis.stripe_payments.payment;

/**
 * Published when a Payment reaches SUCCEEDED. Carries only our own ids + money —
 * no com.stripe.* types, no Order import (plan.md §9). Whoever cares (the order/
 * listener) filters on referenceType and reacts.
 */
public record PaymentSucceededEvent(
        Long paymentId,
        PaymentReferenceType referenceType,
        Long referenceId,
        Long userId,
        long amount,
        String currency) {
}