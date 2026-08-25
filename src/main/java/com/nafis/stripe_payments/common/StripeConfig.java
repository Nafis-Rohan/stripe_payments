//package com.nafis.stripe_payments.common;
//
//import com.stripe.Stripe;
//import jakarta.annotation.PostConstruct;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.stereotype.Component;
//
//@Component
//public class StripeConfig {
//
//    @Value("${stripe.api.secret-key}")
//    private String secretKey;
//
//    @PostConstruct  //Run this method automatically after Spring creates this object.
//    public void init(){
//        Stripe.apiKey = secretKey;
//    }
//
//
//}

//@Configuration → This class contains setup/configuration instructions
//@Bean → Create this object and store/manage it in the Spring Container


package com.nafis.stripe_payments.common;

import com.stripe.StripeClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StripeConfig {

    @Value("${stripe.api.secret-key}")
    private String secretKey;

    @Value("${stripe.api.max-network-retries:2}")
    private int maxNetworkRetries;

    @Bean
    public StripeClient stripeClient() {
        return StripeClient.builder()
                .setApiKey(secretKey)
                .setMaxNetworkRetries(maxNetworkRetries)
                .build();
    }
}