package com.nafis.stripe_payments.payment.event;

import com.nafis.stripe_payments.payment.PaymentReferenceType;

/**
 * Published when a payment attempt fails (the PaymentIntent fell back to
 * requires_payment_method with an error). failureCode / failureMessage are
 * Stripe's, passed as plain strings so the domain can show a message without
 * touching the SDK.
 */
public record PaymentFailedEvent(
        Long paymentId,
        PaymentReferenceType referenceType,
        Long referenceId,
        Long userId,
        String failureCode,
        String failureMessage) {
}