package com.nafis.stripe_payments.webhook;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/webhooks/stripe")
public class WebhookController {

    private final WebhookEventRepository webhookEventRepository;
    private final WebhookEventProcessor webhookEventProcessor;

    @Value("${stripe.webhook.secret}")
    private String webhookSecret;

    @PostMapping
    public ResponseEntity<String> receive(@RequestBody String payload,
                                          @RequestHeader("Stripe-Signature") String sigHeader) {

        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.warn("Webhook signature verification failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("invalid signature");
        }

        if (webhookEventRepository.existsByStripeEventId(event.getId())) {
            log.info("Duplicate Stripe webhook, already recorded: id={}, type={}", event.getId(), event.getType());
            return ResponseEntity.ok("ok");
        }

        WebhookEvent webhookEvent = new WebhookEvent();
        webhookEvent.setStripeEventId(event.getId());
        webhookEvent.setType(event.getType());
        webhookEvent.setPayload(payload);
        webhookEvent = webhookEventRepository.save(webhookEvent);

        webhookEventProcessor.process(webhookEvent.getId());

        log.info("Recorded Stripe webhook: id={}, type={}", event.getId(), event.getType());
        return ResponseEntity.ok("ok");
    }
}