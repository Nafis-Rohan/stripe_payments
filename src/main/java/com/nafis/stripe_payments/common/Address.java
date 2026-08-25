package com.nafis.stripe_payments.common;

import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Embeddable
public class Address {

    private String line1;
    private String line2;
    private String city;
    private String state;
    private String postalCode;
    private String country; // ISO 3166-1 alpha-2, e.g. "US" — Stripe requires this format
}

