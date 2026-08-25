package com.nafis.stripe_payments.order;

import com.nafis.stripe_payments.common.BaseEntity;
import com.nafis.stripe_payments.product.Product;
import com.nafis.stripe_payments.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "orders")
public class Order extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @Column(nullable = false)
    private String currency;

    @Column(nullable = false)
    private Long subtotal = 0L;

    @Column(nullable = false)
    private Long discountAmount = 0L;

    @Column(nullable = false)
    private Long shippingAmount = 0L;

    @Column(nullable = false)
    private Long taxAmount = 0L;

    @Column(nullable = false)
    private Long total = 0L;

    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.PENDING;


    private static final Map<OrderStatus,
            Set<OrderStatus>> ALLOWED_TRANSITIONS = new
            EnumMap<>(OrderStatus.class);
    static {
        ALLOWED_TRANSITIONS.put(OrderStatus.PENDING,
                EnumSet.of(OrderStatus.PAID,
                        OrderStatus.FAILED, OrderStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(OrderStatus.FAILED,
                EnumSet.of(OrderStatus.PENDING));
        ALLOWED_TRANSITIONS.put(OrderStatus.PAID,
                EnumSet.of(OrderStatus.PARTIALLY_REFUNDED,
                        OrderStatus.REFUNDED, OrderStatus.DISPUTED));
        ALLOWED_TRANSITIONS.put(OrderStatus.PARTIALLY_REFUNDED,
                EnumSet.of(OrderStatus.REFUNDED,
                        OrderStatus.DISPUTED));
        ALLOWED_TRANSITIONS.put(OrderStatus.DISPUTED,
                EnumSet.of(OrderStatus.PAID,
                        OrderStatus.REFUNDED, OrderStatus.PARTIALLY_REFUNDED));
        ALLOWED_TRANSITIONS.put(OrderStatus.REFUNDED,
                EnumSet.noneOf(OrderStatus.class));
        ALLOWED_TRANSITIONS.put(OrderStatus.CANCELLED,
                EnumSet.noneOf(OrderStatus.class));
    }
    public void transitionTo(OrderStatus newStatus) {
        if (!ALLOWED_TRANSITIONS.get(status).contains(newStatus)) {
            throw new InvalidOrderStatusTransitionException(status, newStatus);
        }
        this.status = newStatus;
    }

    /**
     * Adds a line item, snapshotting the product's current price. All items
     * on one Order must share a currency — Stripe charges one currency per
     * PaymentIntent.
     */
    public OrderItem addItem(Product product, Integer quantity) {
        if (currency == null) {
            currency = product.getCurrency();
        } else if (!currency.equals(product.getCurrency())) {
            throw new IllegalArgumentException(
                    "Order is in " + currency + ", cannot add a " + product.getCurrency() + " item");
        }

        OrderItem item = new OrderItem();
        item.setOrder(this);
        item.setProduct(product);
        item.setQuantity(quantity);
        item.setUnitAmount(product.getUnitAmount());
        item.setCurrency(product.getCurrency());
        items.add(item);
        return item;
    }

    /** Recomputes subtotal and total from current line items. Call after adding all items. */
    public void recalculateTotals() {
        this.subtotal = items.stream().mapToLong(OrderItem::getLineTotal).sum();
        this.total = subtotal - discountAmount + shippingAmount + taxAmount;
    }
}