package com.nafis.stripe_payments.order.dto;



import com.nafis.stripe_payments.order.Order;
import com.nafis.stripe_payments.order.OrderItem;
import com.nafis.stripe_payments.order.OrderStatus;

import java.util.List;


/** why nexted record of OrderItemResponse OrderItemResponse ?
 * is only used as part of an OrderResponse
 */

public record OrderResponse(
        Long id,
        Long userId,
        String currency,
        Long subtotal,
        Long discountAmount,
        Long shippingAmount,
        Long taxAmount,
        Long total,
        OrderStatus status,
        List<OrderItemResponse> items
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getUser().getId(),
                order.getCurrency(),
                order.getSubtotal(),
                order.getDiscountAmount(),
                order.getShippingAmount(),
                order.getTaxAmount(),
                order.getTotal(),
                order.getStatus(),

                order.getItems().stream().map(OrderItemResponse::from).toList()
        );
    }
    public record OrderItemResponse(
            Long productId,
            String productName,
            Integer quantity,
            Long unitAmount,
            Long lineTotal
    ) {
        public static OrderItemResponse from(OrderItem item) {
            return new OrderItemResponse(
                    item.getProduct().getId(),
                    item.getProduct().getName(),
                    item.getQuantity(),
                    item.getUnitAmount(),
                    item.getLineTotal()
            );
        }
    }
}
