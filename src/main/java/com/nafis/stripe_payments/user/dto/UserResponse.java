package com.nafis.stripe_payments.user.dto;

import com.nafis.stripe_payments.common.Address;
import com.nafis.stripe_payments.user.User;

public record UserResponse(
        Long id,
        String name,
        String email,
        String stripeCustomerId,
        AddressRequest address
) {
    public static UserResponse from(User user) {
        Address a = user.getAddress();
        AddressRequest addressResponse = a == null ? null : new AddressRequest(
                a.getLine1(), a.getLine2(), a.getCity(), a.getState(), a.getPostalCode(), a.getCountry());
        return new UserResponse(user.getId(), user.getName(), user.getEmail(),
                user.getStripeCustomerId(), addressResponse);
    }
}