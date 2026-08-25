package com.nafis.stripe_payments.common;

import com.stripe.model.StripeObject;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * One-line request-id logging for successful Stripe calls, so a dashboard
 * object can always be traced back to the log line that created it.
 */
@Slf4j
@UtilityClass
public class StripeLogging {

    public void logSuccess(String operation, StripeObject result) {
        String requestId = result.getLastResponse() != null
                ? result.getLastResponse().requestId()
                : "unknown";
        log.info("Stripe call succeeded: operation={}, requestId={}", operation, requestId);
    }
}
