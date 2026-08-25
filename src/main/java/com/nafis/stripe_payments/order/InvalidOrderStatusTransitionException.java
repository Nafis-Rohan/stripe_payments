package com.nafis.stripe_payments.order;

import com.nafis.stripe_payments.common.ConflictException;

public class InvalidOrderStatusTransitionException extends ConflictException {

    public InvalidOrderStatusTransitionException(OrderStatus from, OrderStatus to) {
        super("Cannot transition Order from " + from + " to " + to);
    }
}
