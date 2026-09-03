package com.nafis.stripe_payments.webhook;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long> {
    Optional<WebhookEvent> findByStripeEventId(String stripeEventId);
    boolean existsByStripeEventId(String stripeEventId);
}