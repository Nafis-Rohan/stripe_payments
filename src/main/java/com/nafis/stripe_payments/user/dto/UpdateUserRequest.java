package com.nafis.stripe_payments.user.dto;

public record UpdateUserRequest(String name, String email, AddressRequest address) {
}