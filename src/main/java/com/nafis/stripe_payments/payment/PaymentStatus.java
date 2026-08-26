package com.nafis.stripe_payments.payment;

public enum PaymentStatus {
    REQUIRES_PAYMENT_METHOD, // Waiting for customer to enter card details (or card was declined)
    REQUIRES_CONFIRMATION,// Card details are saved, waiting for backend to officially trigger the charge
    REQUIRES_ACTION,// Bank requires extra customer authentication (e.g., 3D Secure SMS code)
    PROCESSING,// Money is actively in transit (common for bank transfers, quick for cards)
    REQUIRES_CAPTURE,// Funds are "frozen" on the card, waiting for you to capture (collect) them
    SUCCEEDED,// Payment is completely finished and successful
    CANCELED   // Payment was intentionally aborted or expired
}