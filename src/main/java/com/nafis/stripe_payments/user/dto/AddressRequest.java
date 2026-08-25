package com.nafis.stripe_payments.user.dto;

public record AddressRequest(
        String line1,
        String line2,
        String city,
        String state,
        String postalCode,
        String country
) {
}