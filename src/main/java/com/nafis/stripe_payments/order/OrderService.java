package com.nafis.stripe_payments.order;


import com.nafis.stripe_payments.order.dto.OrderItemRequest;
import com.nafis.stripe_payments.product.Product;
import com.nafis.stripe_payments.product.ProductRepository;
import com.nafis.stripe_payments.user.User;
import com.nafis.stripe_payments.user.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    @Transactional
    public Order createOrder(Long userId, List<OrderItemRequest>
            itemRequests) {
        if (itemRequests == null || itemRequests.isEmpty()) {
            throw new IllegalArgumentException("An order needs at least one item");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));

        Order order = new Order();
        order.setUser(user);

        for (OrderItemRequest itemRequest : itemRequests) {
            if (itemRequest.quantity() == null || itemRequest.quantity() < 1)
            {
                throw new IllegalArgumentException("quantity must be at least 1");
            }
            Product product =
                    productRepository.findById(itemRequest.productId())
                            .orElseThrow(() -> new EntityNotFoundException("Product not found: " +
                                    itemRequest.productId()));
            order.addItem(product, itemRequest.quantity());
        }
        order.recalculateTotals();

        return orderRepository.save(order);
    }


    @Transactional
    public Order cancelOrder(Long id) {
        Order order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + id));
        order.transitionTo(OrderStatus.CANCELLED);
        return orderRepository.save(order);
    }

    public List<Order> listOrdersForUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
        return orderRepository.findByUser(user);
    }



    public Order getOrder(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + id));
    }
}

