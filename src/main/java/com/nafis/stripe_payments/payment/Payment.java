package com.nafis.stripe_payments.payment;

import com.nafis.stripe_payments.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "payments",
        uniqueConstraints = @UniqueConstraint(columnNames = "stripe_payment_intent_id"),
        indexes = @Index(name = "idx_payment_reference", columnList = "reference_type, reference_id")
)
public class Payment extends BaseEntity {

    @Column(name = "stripe_payment_intent_id")
    private String stripePaymentIntentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", nullable = false)
    private PaymentReferenceType referenceType;

    @Column(name = "reference_id", nullable = false)
    private Long referenceId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long amount;

    @Column(nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status = PaymentStatus.REQUIRES_PAYMENT_METHOD;
}
