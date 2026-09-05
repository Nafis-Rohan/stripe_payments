package com.nafis.stripe_payments.webhook;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookEventProcessor {

    private final WebhookEventRepository webhookEventRepository;

    // Runs on Spring's async executor, off the request thread that answered
    // Stripe. This is the boundary that keeps the controller's response time
    // independent of how long handling an event takes — Stripe gives us
    // ~10s before it decides the delivery failed and retries.
    @Async
    public void process(Long webhookEventId) {
        WebhookEvent webhookEvent = webhookEventRepository.findById(webhookEventId)
                .orElseThrow(() -> new IllegalStateException("WebhookEvent " + webhookEventId + " vanished before processing"));

        // TODO: dispatch to the StripeEventHandler that supports webhookEvent.getType()
        // — that's the next step. For now this just proves the async boundary works.
        log.info("Processing webhook event: id={}, type={}", webhookEvent.getStripeEventId(), webhookEvent.getType());

        webhookEvent.setProcessedAt(Instant.now());
        webhookEventRepository.save(webhookEvent);
    }
}