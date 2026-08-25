package com.nafis.stripe_payments.common;

import com.nafis.stripe_payments.order.Order;
import com.nafis.stripe_payments.order.OrderRepository;
import com.nafis.stripe_payments.product.Product;
import com.nafis.stripe_payments.product.ProductRepository;
import com.nafis.stripe_payments.user.User;
import com.nafis.stripe_payments.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;

    @Override
    public void run(String... args) {
        seedUsers();
        seedProducts();
        seedOrders();
    }

    private void seedUsers() {
        if (userRepository.count() > 0) {
            log.info("Users already seeded, skipping");
            return;
        }

        User alice = new User();
        alice.setName("Alice Example");
        alice.setEmail("alice@example.com");
        userRepository.save(alice);

        User bob = new User();
        bob.setName("Bob Example");
        bob.setEmail("bob@example.com");
        userRepository.save(bob);

        log.info("Seeded 2 test users");
    }

    private void seedProducts() {
        if (productRepository.count() > 0) {
            log.info("Products already seeded, skipping");
            return;
        }

        Product mug = new Product();
        mug.setName("Coffee Mug");
        mug.setDescription("A plain ceramic mug");
        mug.setUnitAmount(1500L);
        mug.setCurrency("usd");
        productRepository.save(mug);

        Product tshirt = new Product();
        tshirt.setName("T-Shirt");
        tshirt.setDescription("100% cotton tee");
        tshirt.setUnitAmount(2500L);
        tshirt.setCurrency("usd");
        productRepository.save(tshirt);

        log.info("Seeded 2 test products");
    }

    private void seedOrders() {
        if (orderRepository.count() > 0) {
            log.info("Orders already seeded, skipping");
            return;
        }

        User alice = userRepository.findByEmail("alice@example.com")
                .orElseThrow(() -> new IllegalStateException("Expected seeded user alice@example.com"));

        Product mug = productRepository.findByActiveTrue().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Expected at least one seeded product"));

        Order order = new Order();
        order.setUser(alice);
        order.addItem(mug, 1);
        order.recalculateTotals();
        orderRepository.save(order);

        log.info("Seeded 1 test order (PENDING) for {}", alice.getEmail());
    }
}