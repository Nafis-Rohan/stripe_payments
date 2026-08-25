package com.nafis.stripe_payments.common;

/**
 * A request conflicts with the current state of the resource it targets —
 * maps to HTTP 409. Domain packages extend this for their own "invalid state
 * transition" exceptions so GlobalExceptionHandler never has to import a
 * domain package to handle them.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}