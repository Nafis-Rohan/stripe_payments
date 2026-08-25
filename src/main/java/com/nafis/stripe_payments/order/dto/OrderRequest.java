package com.nafis.stripe_payments.order.dto;

import java.util.List;

public record OrderRequest(Long userId, List<OrderItemRequest> items) {
}