package com.nafis.stripe_payments.order;

import com.nafis.stripe_payments.payment.PaymentReferenceType;
import com.nafis.stripe_payments.payment.event.PaymentFailedEvent;
import com.nafis.stripe_payments.payment.event.PaymentSucceededEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPaymentEventListener {

    private final OrderRepository orderRepository;

    @EventListener //Spring, whenever a PaymentSucceededEvent happens, run this method.
    public void onPaymentSucceeded(PaymentSucceededEvent event) {
        if (event.referenceType() != PaymentReferenceType.ORDER) {
            return;
        }
        transition(event.referenceId(), OrderStatus.PAID);
    }

    @EventListener
    public void onPaymentFailed(PaymentFailedEvent event) {
        if (event.referenceType() != PaymentReferenceType.ORDER) {
            return;
        }
        transition(event.referenceId(), OrderStatus.FAILED);
    }

    @Transactional
    void transition(Long orderId, OrderStatus target) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalStateException(
                        "Order not found for payment event: " + orderId));
        try {
            order.transitionTo(target);
            orderRepository.save(order);
        } catch (InvalidOrderStatusTransitionException e) {
            // A redelivered/duplicate event, or the Order alreadymoved on its own.
            // State-convergent: never crash the listener over astale transition (plan.md §5).
            log.warn("Ignoring {} for order {}: {}", target, orderId,
                    e.getMessage());
        }
    }
}