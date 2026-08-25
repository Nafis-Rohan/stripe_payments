package com.nafis.stripe_payments.order;

import com.nafis.stripe_payments.common.BaseEntity;
import com.nafis.stripe_payments.product.Product;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "order_items")
public class OrderItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private Integer quantity;

    // Snapshot of Product.unitAmount at purchase time. A later price change
    // on Product must never retroactively alter what a past order cost.
    @Column(nullable = false)
    private Long unitAmount;

    @Column(nullable = false)
    private String currency;

    public Long getLineTotal() {
        return unitAmount * quantity;
    }
}

