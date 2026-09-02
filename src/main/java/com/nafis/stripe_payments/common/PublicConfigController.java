package com.nafis.stripe_payments.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class PublicConfigController {

    @Value("${stripe.api.publishable-key}")
    private String publishableKey;

    @GetMapping("/api/config")
    public Map<String, String> config() {
        return Map.of("publishableKey", publishableKey);
    }
}