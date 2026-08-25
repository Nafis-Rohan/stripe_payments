package com.nafis.stripe_payments.order;

import com.nafis.stripe_payments.user.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUser(User user);

    /** Imagine two requests happen at exactly the same time.
     * Request A → update Order 10
     * Request B → update Order 10
     * Both might try to modify the same order.
     * Possible race condition
     * so @Lock make it, Request A is working on this row. Request B, wait.*/
    @Lock(LockModeType.PESSIMISTIC_WRITE)//When I fetch this order, lock it for writing.
    @Query("select o from Order o where o.id = :id")//Find the Order whose ID equals the ID I provide.
    Optional<Order> findByIdForUpdate(@Param("id") Long id);
}