# change.md — Migration to the `plan.md` architecture

Applied 2026-08-21. This restructures what already existed so it matches the new rule in
`plan.md`: **`payment/` never imports a domain package; domains import `payment/`.**

Nothing new was built ahead. Everything below is a *move* or a *removal* of code that
already existed — the project is at exactly the same functional point as before, just
arranged correctly. Verified with `mvnw compile` → BUILD SUCCESS.

---

## 1. `Order` entity — moved out of `payment/`, Stripe columns removed

**1. Before** — `payment/Order.java`

```java
package com.nafis.stripe_payments.payment;

@Entity
@Table(name = "orders")
public class Order extends BaseEntity {
    // ...user, product, quantity, amount, currency, status...

    @Column(name = "stripe_payment_intent_id", unique = true)
    private String stripePaymentIntentId;

    @Column(name = "stripe_checkout_session_id", unique = true)
    private String stripeCheckoutSessionId;
}
```

**1. After** — `order/Order.java`

```java
package com.nafis.stripe_payments.order;

@Entity
@Table(name = "orders")
public class Order extends BaseEntity {
    // ...user, product, quantity, amount, currency, status...
    // both Stripe columns deleted
}
```

**Why:** `Order` is the domain, not the payment. Two reasons the columns had to go:

- They made `payment` and `order` inseparable — the whole point of the restructure.
- `stripe_payment_intent_id` was `unique` **and singular**, so the `FAILED → PENDING`
  retry path in `transitionTo()` would create a second PaymentIntent in Stripe while
  overwriting the reference to the first. The failed attempt vanished. Phase 2's
  `Payment` entity replaces this with one row per attempt.

`transitionTo()` and the `ALLOWED_TRANSITIONS` map are **unchanged** — moved verbatim.

---

## 2. `OrderStatus` enum — moved

**2. Before:** `payment/OrderStatus.java` → `package com.nafis.stripe_payments.payment;`

**2. After:** `order/OrderStatus.java` → `package com.nafis.stripe_payments.order;`

Contents identical. **Why:** domain vocabulary (`PENDING`/`PAID`/`REFUNDED`) belongs to
the domain. Stripe vocabulary gets its own `PaymentStatus` enum in `payment/` in Phase 2.

---

## 3. `OrderRepository` — moved, and two finder methods deleted

**3. Before** — `payment/OrderRepository.java`

```java
package com.nafis.stripe_payments.payment;

public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByStripePaymentIntentId(String stripePaymentIntentId);
    Optional<Order> findByStripeCheckoutSessionId(String stripeCheckoutSessionId);
    List<Order> findByUser(User user);
}
```

**3. After** — `order/OrderRepository.java`

```java
package com.nafis.stripe_payments.order;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUser(User user);
}
```

**Why this was mandatory, not optional:** Spring Data derives queries from property
names at startup. Leaving `findByStripePaymentIntentId` after deleting the field would
**crash the application on boot**, not fail at runtime. The unused `Optional` import went
with them.

Looking up a payment by its Stripe id becomes `PaymentRepository`'s job in Phase 2.

---

## 4. `InvalidOrderStatusTransitionException` — moved

**4. Before:** `payment/InvalidOrderStatusTransitionException.java`

**4. After:** `order/InvalidOrderStatusTransitionException.java`

Contents identical. **Why:** it's thrown by `Order.transitionTo()`, so it lives with the
entity that throws it. Still no `import` needed inside `Order.java` — same package.

---

## 5. `payment/OrderService.java` — deleted

**5. Before:** a service in `payment/` that loaded an `Order`, created a PaymentIntent,
and called `order.setStripePaymentIntentId(...)`.

**5. After:** deleted. No replacement yet.

**Why:** three separate problems.

- It called `order.setStripePaymentIntentId(...)`, a setter that no longer exists — it
  would not compile.
- A class named `OrderService` that talks to Stripe sits on the wrong side of the line.
  Order-lifecycle logic belongs in `order/OrderService`; Stripe logic belongs in
  `payment/PaymentService`. It was doing both.
- Its replacement is `PaymentService.createPaymentIntent(PaymentRequest)` in Phase 2,
  which takes amount/currency/reference and never sees an `Order` at all.

This was the file given before the restructure was decided — it is superseded, not lost.

---

## 6. `common/DataSeeder.java` — imports updated

**6. Before**

```java
import com.nafis.stripe_payments.payment.Order;
import com.nafis.stripe_payments.payment.OrderRepository;
```

**6. After**

```java
import com.nafis.stripe_payments.order.Order;
import com.nafis.stripe_payments.order.OrderRepository;
```

**Why:** only the imports changed — `seedOrders()` never touched the Stripe columns, so
the seeding logic is untouched. Still seeds 2 users, 2 products, 1 `PENDING` order.

---

## 7. `payment/` package — now empty and removed

**7. Before:** `payment/` held 5 files, all of them Order-related.

**7. After:** directory deleted. It gets recreated in Phase 2 when
`PaymentReferenceType.java` — the first file that genuinely belongs there — is written.

**Why:** matches the "packages are created on demand" convention. An empty `payment/`
folder sitting around would be a folder waiting to be filled with the wrong thing.

---

## Current file layout

```
common/   BaseEntity, StripeConfig, ApiError, GlobalExceptionHandler, DataSeeder
user/     User, UserRepository, UserService
product/  Product, ProductRepository
order/    Order, OrderStatus, OrderRepository, InvalidOrderStatusTransitionException
```

---

## One thing to know about your database

`application.yml` uses `ddl-auto: update`, and Hibernate's `update` mode **never drops
columns**. The `stripe_payment_intent_id` and `stripe_checkout_session_id` columns will
still physically exist in your `orders` table — they're simply no longer mapped to
anything, so they'll sit there empty and unused.

Harmless. If you want them gone, either drop them by hand:

```sql
ALTER TABLE orders
  DROP COLUMN stripe_payment_intent_id,
  DROP COLUMN stripe_checkout_session_id;
```

...or just drop the whole `stripe_playground` database and let the seeder rebuild it —
there's only test data in there.

---

## What was deliberately NOT done

Stopping exactly at your current progress, per your request. These are on the tracker but
untouched, so nothing is skipped for learning:

- `Payment` entity, `PaymentStatus`, `PaymentReferenceType` — Phase 2
- `PaymentService`, PaymentIntent creation — Phase 2
- Locking down `setStatus` so `transitionTo()` is the only path — Phase 1, still open
- `order/OrderService`, Order REST controller — Phase 1, still open
- Spring events (`PaymentSucceededEvent`) and the `order/` listener — Phases 1 & 2
