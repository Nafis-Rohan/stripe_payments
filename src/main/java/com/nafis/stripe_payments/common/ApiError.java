package com.nafis.stripe_payments.common;

import java.time.Instant;

public record ApiError(Instant timestamp,
                       int status,
                       String error,
                       String message,
                       String code,
                       String path) {

}
