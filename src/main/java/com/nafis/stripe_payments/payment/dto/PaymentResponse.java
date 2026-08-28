package com.nafis.stripe_payments.payment.dto;

import com.nafis.stripe_payments.payment.Payment;
import com.nafis.stripe_payments.payment.PaymentReferenceType;
import com.nafis.stripe_payments.payment.PaymentStatus;

import java.time.Instant;

public record PaymentResponse(
        Long id,
        String stripePaymentIntentId,
        PaymentReferenceType referenceType,
        Long referenceId,
        Long userId,
        long amount,
        String currency,
        PaymentStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(
                p.getId(),
                p.getStripePaymentIntentId(),
                p.getReferenceType(),
                p.getReferenceId(),
                p.getUserId(),
                p.getAmount(),
                p.getCurrency(),
                p.getStatus(),
                p.getCreatedAt(),
                p.getUpdatedAt());
    }
}