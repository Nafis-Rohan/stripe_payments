package com.nafis.stripe_payments.webhook;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/webhooks/stripe")
public class WebhookController {

    @PostMapping
    public String receive(@RequestBody String payload,
                          @RequestHeader("Stripe-Signature") String sigHeader) {
        log.info("Received Stripe webhook: {} bytes, signature header present={}",
                payload.length(), sigHeader != null);
        return "ok";
    }
}