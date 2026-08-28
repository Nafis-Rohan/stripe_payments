package com.nafis.stripe_payments.payment.event;

import com.nafis.stripe_payments.payment.PaymentReferenceType;

/**
 * Published when a Payment reaches SUCCEEDED. Carries only our own ids + money —
 * no com.stripe.* types, no Order import (plan.md §9). The order/ listener filters
 * on referenceType and reacts.
 */
public record PaymentSucceededEvent(
        Long paymentId,
        PaymentReferenceType referenceType,
        Long referenceId,
        Long userId,
        long amount,
        String currency) {
}