package com.nafis.stripe_payments.order;

import com.nafis.stripe_payments.order.dto.OrderRequest;
import com.nafis.stripe_payments.order.dto.OrderResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@RequestBody OrderRequest request) {
        Order order = orderService.createOrder(request.userId(), request.items());
        return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(order));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable Long id) {
        return ResponseEntity.ok(OrderResponse.from(orderService.getOrder(id)));
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>> listOrders(@RequestParam Long userId) {
        List<OrderResponse> responses = orderService.listOrdersForUser(userId).stream()
                .map(OrderResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(@PathVariable Long id) {
        return ResponseEntity.ok(OrderResponse.from(orderService.cancelOrder(id)));
    }
}
