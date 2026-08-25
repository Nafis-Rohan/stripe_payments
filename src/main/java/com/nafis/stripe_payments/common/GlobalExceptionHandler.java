package com.nafis.stripe_payments.common;



import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.AuthenticationException;
import com.stripe.exception.CardException;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.RateLimitException;
import com.stripe.exception.StripeException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@Slf4j //automatically creates a logger for your class.
@RestControllerAdvice //Apply this class's logic globally to controllers. This is a global exception handler for my REST API.
public class GlobalExceptionHandler {



    // Card declined, expired, CVC failed, etc. — the customer
    //needs to act, not us.
    @ExceptionHandler(CardException.class)//If a StripeException occurs, call this method.
    public ResponseEntity<ApiError>
    handleCardException(CardException ex, HttpServletRequest request)
    {
        String message = ex.getUserMessage() != null ?
                ex.getUserMessage() : ex.getMessage();
        log.warn("Stripe card error: requestId={}, code={}, message={}",
                ex.getRequestId(), ex.getCode(), message);
        return build(HttpStatus.PAYMENT_REQUIRED, message,
                ex.getCode(), request);
    }
    // We sent Stripe bad parameters — a bug in our code, not the
    //customer's fault.
    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ApiError>
    handleInvalidRequest(InvalidRequestException ex,
                         HttpServletRequest request) {
        log.error("Invalid request sent to Stripe: requestId={}, code={}, message={}",
                ex.getRequestId(), ex.getCode(), ex.getMessage(), ex);
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(),
                ex.getCode(), request);
    }

    // We're calling Stripe too fast — caller should back off and
    //retry.
    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<ApiError>
    handleRateLimit(RateLimitException ex, HttpServletRequest request)
    {
        log.warn("Stripe rate limit hit: requestId={}, message={}",
                ex.getRequestId(), ex.getMessage());
        return build(HttpStatus.TOO_MANY_REQUESTS,
                ex.getMessage(), ex.getCode(), request);
    }

    // Network failure reaching Stripe — transient, safe to retry.
    @ExceptionHandler(ApiConnectionException.class)
    public ResponseEntity<ApiError>
    handleApiConnection(ApiConnectionException ex, HttpServletRequest
            request) {
        log.error("Could not reach Stripe: requestId={}, message={}",
                ex.getRequestId(), ex.getMessage(), ex);
        return build(HttpStatus.SERVICE_UNAVAILABLE, "Payment " +
                "provider is temporarily unavailable", null, request);
    }

    // Our API key is invalid/misconfigured — never leak that
    //detail to the caller.
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError>
    handleAuthentication(AuthenticationException ex,
                         HttpServletRequest request) {
        log.error("Stripe authentication failed — check the configured API key: requestId={}, message={}",
                ex.getRequestId(), ex.getMessage(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Payment provider " +
                "configuration error", null, request);
    }

    // Fallback for any other Stripe error type we haven't special-cased.
    @ExceptionHandler(StripeException.class)
    public ResponseEntity<ApiError> handleStripeException(StripeException ex, HttpServletRequest request) {
        log.error("Unhandled Stripe error: requestId={}, code={}, message={}",
                ex.getRequestId(), ex.getCode(), ex.getMessage(), ex);
        return build(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex.getCode(), request);
    }

    // A domain resource is already in a state that conflicts with the request
    // (e.g. an Order status transition that isn't allowed). Domain packages
    // extend ConflictException so this handler never has to import them.
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException ex, HttpServletRequest request) {
        log.warn("Conflict: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, ex.getMessage(), null, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), null, request);
    }

    private ResponseEntity<ApiError> build(HttpStatus status,
                                           String message,
                                           String code,
                                           HttpServletRequest request) {
        ApiError body = new ApiError(Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                code,
                request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
