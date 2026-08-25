package com.nafis.stripe_payments.user;

import com.nafis.stripe_payments.common.Address;
import com.nafis.stripe_payments.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "app_user")
public class User extends BaseEntity{

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "stripe_customer_id", unique = true)
    private String stripeCustomerId;

    @Embedded
    private Address address;
}
