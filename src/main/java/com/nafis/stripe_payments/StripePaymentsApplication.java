package com.nafis.stripe_payments;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class StripePaymentsApplication {

	public static void main(String[] args)
	{
		SpringApplication.run(StripePaymentsApplication.class, args);
	}

}
