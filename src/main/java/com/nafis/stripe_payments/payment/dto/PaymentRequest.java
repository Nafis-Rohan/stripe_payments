package com.nafis.stripe_payments.payment.dto;

import com.nafis.stripe_payments.payment.PaymentReferenceType;

public record PaymentRequest( Long userId, String stripeCustomerId, long amount, String currency,
                                                        PaymentReferenceType referenceType,
                                                        Long referenceId) {
    //Compact Constructor - It runs automatically the exact moment a new PaymentRequest is created
    public PaymentRequest {
        if (userId == null) throw new IllegalArgumentException("userId is required");
        if (stripeCustomerId == null || stripeCustomerId.isBlank())
            throw new IllegalArgumentException("stripeCustomerId is required");
        if (amount <= 0) throw new IllegalArgumentException("amount must be positive");
        if (currency == null || currency.isBlank())
            throw new IllegalArgumentException("currency is required");
        if (referenceType == null) throw new IllegalArgumentException("referenceType is required");
        if (referenceId == null) throw new IllegalArgumentException("referenceId is required");
    }
}
