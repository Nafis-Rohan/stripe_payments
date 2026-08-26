package com.nafis.stripe_payments.payment.dto;

import com.nafis.stripe_payments.payment.PaymentStatus;

public record PaymentResult(
        Long paymentId,
        String stripePaymentIntentId,
        String clientSecret,
        PaymentStatus status) {


}
