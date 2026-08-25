package com.nafis.stripe_payments.product;

import com.nafis.stripe_payments.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "product")
public class Product extends BaseEntity {

    @Column(nullable = false)
    private String name;

    private String description;

    //never store money as float/double, it avoids rounding errors when you send amounts
    // to the PaymentIntent API.
    @Column(name = "unit_amount", nullable = false)
    private Long unitAmount;

    @Column(nullable = false)
    private String currency = "usd";

    @Column(name = "stripe_product_id", unique = true)
    private String stripeProductId;

    @Column(name = "stripe_price_id", unique = true)
    private String stripePriceId;

    @Column(nullable = false)
    private boolean active = true;
}
