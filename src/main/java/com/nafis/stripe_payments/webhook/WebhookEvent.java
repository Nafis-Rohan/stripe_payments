package com.nafis.stripe_payments.webhook;

import com.nafis.stripe_payments.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
@Getter
@Setter
@Entity
@Table(name = "webhook_events",
        uniqueConstraints = @UniqueConstraint(columnNames = "stripe_event_id")
)
public class WebhookEvent extends BaseEntity {

    @Column(name = "stripe_event_id", nullable = false)
    private String stripeEventId;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "processed_at")
    private Instant processedAt;
}

