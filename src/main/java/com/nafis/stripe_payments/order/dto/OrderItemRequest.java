package com.nafis.stripe_payments.order.dto;

public record OrderItemRequest(Long productId, Integer quantity) {
}