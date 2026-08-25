# Stripe Playground — Architecture Plan

Decided 2026-08-21, rescoped 2026-08-24. This file explains *why* things are shaped the
way they are. `task.md` tracks *what* gets built and in what order.

---

## 1. Goal and scope

Learn the **Stripe payments surface as it applies to e-commerce orders**, by building each
capability for real against test keys.

**In scope:** everything that happens between "customer wants to buy an Order" and "money
is settled, refunded, or disputed" — PaymentIntents, hosted Checkout, saved cards,
off-session charging, alternative payment methods, webhooks, refunds, disputes, fraud,
discounts, shipping, tax, fees and payouts. Plus the two adjacent Billing products that
share the same machinery: subscriptions and standalone invoicing. Plus Connect, because
marketplace orders are still orders.

**Out of scope, deliberately** — see §13 for the reasoning:
Issuing, Terminal, Identity, Financial Connections, Treasury, and the legacy Charges API.

**Parked for the future:** a second domain (cinema ticket booking — `Screening`,
`Booking`) to prove `payment/` is genuinely reusable. **No code for this now.** It exists
in this document only as the reason §2's rule is worth obeying. If you ever want it, the
work is: one new entity, one new enum constant, one event listener — and nothing inside
`payment/` should need to change.

---

## 2. The one rule

> `payment/` never imports a domain package. Domains import `payment/`.

The dependency arrow points one way, always:

```
order/     ──┐
(cinema/)  ──┼──►  payment/  ──►  common/
(future)   ──┘                    webhook/
```

If `payment` ever needs `import com.nafis.stripe_payments.order.Order`, the design has
broken — that import is exactly what would make the package unusable for anything else.

`payment` knows only four things about what it's charging for:
**amount, currency, who's paying, and an opaque reference back to the caller.**

This rule costs almost nothing to follow and it's the single thing that keeps the Stripe
code from turning into order-shaped spaghetti. Follow it even though cinema is parked.

### Naming convention: the name declares reusability

Decided 2026-08-24. You should be able to tell whether a class survives a domain change
**from its name alone**, without opening it.

| Name | Meaning | Lives in |
|---|---|---|
| `Payment`, `Refund`, `Invoice`, `TaxService` | Domain-constant. Carries to any project unchanged. | the feature package (`payment/`, `refund/`, `tax/`) |
| `OrderInvoice`, `OrderTaxService`, `TicketTax` | Domain-specific. Dies with its domain. | the **domain** package (`order/`, later `ticket/`) |

Two rules make this work:

1. **A prefixed class lives in the domain package, never the feature package.**
   `order/OrderTaxService` is correct — `order` imports `tax`, arrow points the right way.
   `tax/OrderTaxService` is wrong — it makes `tax/` order-coupled, which is the exact thing
   the prefix was warning about. The name and the location have to agree.
2. **Don't prefix defensively.** An unprefixed name is a *promise* that the class is
   reusable. Prefixing "just in case" dilutes the signal until it means nothing. If you
   can't decide, ask: would this class make any sense in a cinema booking app? If yes, no
   prefix.

The payoff: when a second domain arrives, the prefixed files are the complete list of what
needs writing, and the unprefixed ones are the complete list of what doesn't. No archaeology.

---

## 3. Package map

| Package | Owns | Depends on |
|---|---|---|
| `common/` | `BaseEntity`, `StripeConfig`, `ApiError`, `GlobalExceptionHandler`, `DataSeeder`, money helpers | — |
| `user/` | `User`, Stripe Customer sync, addresses | `common` |
| `product/` | `Product` catalogue, prices, tax codes | `common` |
| `order/` | `Order`, `OrderItem`, `OrderStatus`, state machine, totals | `common`, `payment` |
| `payment/` | `Payment`, `PaymentStatus`, PaymentIntent, Checkout, SetupIntent, saved cards, capture | `common`, `webhook` |
| `webhook/` | `WebhookEvent`, signature verify, dedup, router, `StripeEventHandler` interface | `common` |
| `refund/` | `Refund`, `Dispute`, evidence submission | `common`, `payment`, `webhook` |
| `radar/` | risk outcome, review flagging, early fraud warnings (thin) | `common`, `payment` |
| `discount/` | `Coupon`, `PromotionCode` — generic mechanics | `common` |
| `tax/` | Stripe Tax calls, tax codes, calculation | `common` |
| `ledger/` | balance transactions, Stripe fees, payouts, reporting | `common`, `payment` |
| `billing/` | `Plan`, `Subscription`, `Invoice`, Customer Portal, dunning | `common`, `webhook` |
| `invoicing/` | `ManualInvoice`, invoice items, credit notes | `common`, `webhook` |
| `connect/` | `Seller`, connected accounts, split payments, transfers | `common`, `payment`, `webhook` |

Packages are created **on demand**, when the first file that lives in one is written —
not all upfront. The table is a map of where things will land, not a folder structure to
create today.

`refund/` stays separate from `payment/` rather than folding in, to match Stripe's own
product split — a Refund and a Dispute have their own lifecycles, webhooks, and money
movement, and lumping them into `PaymentService` produces one class that does everything.

Order-specific pricing (assembling subtotal → discount → shipping → tax → total, deciding
which tax code applies, shipping rules) lives in `order/` as `OrderPricingService` /
`OrderTaxService` — **not** in `tax/` or `discount/`, which stay generic. Shipping is
entirely order-shaped (a cinema ticket doesn't ship), so it has no feature package at all.

---

## 4. Money: the rules that prevent the classic bugs

Every amount in this project is an **integer in the currency's minor unit**, stored
alongside its currency code. `long amount` + `String currency`, never `BigDecimal` alone
and never a bare number.

- **Never hardcode `/100` or `* 100`.** JPY and KRW are zero-decimal — ¥1000 is
  `amount = 1000`, not `100000`. BHD and KWD are *three*-decimal. A `/100` scattered
  through the code is a bug that only shows up when you add a currency.
- Formatting for display is a **presentation concern** and happens once, in one helper
  that looks up the currency's exponent. Nothing else divides.
- `Order` carries the full breakdown: `subtotal`, `discountAmount`, `shippingAmount`,
  `taxAmount`, `total`. `total` is what gets charged; the parts exist so the summary
  endpoint and the Stripe line items can agree with each other.
- Line items **snapshot unit price at purchase time**. A `Product` price change must never
  retroactively alter what a past Order cost.
- The amount you send to Stripe and the amount on the Order must be the same number, from
  the same field. Don't recompute it at the boundary.

---

## 5. Idempotency

Two different things share this word, and both matter:

**Outbound — calling Stripe.** Every mutating Stripe call carries an idempotency key.
Not just PaymentIntent creation: refunds, captures, transfers, subscription changes. The
key is derived from *our* stable identity for the operation (e.g. `payment-{paymentId}-create`),
so a retry after a timeout returns the original object instead of charging twice. A random
UUID generated at call time is useless — it changes on retry, which is the exact moment it
needed to be stable.

**Inbound — receiving webhooks.** Stripe delivers at-least-once and *out of order*.
Dedup on `stripeEventId` handles duplicates. Ordering is handled by making handlers
**state-convergent** rather than incremental: a handler sets the Payment to the status the
event describes, and refuses to move a row backwards out of a terminal state. Never write
a handler that does `refundedAmount += x` — a redelivery doubles it.

---

## 6. How `payment` refers back to a domain

`Payment` carries a **polymorphic reference** — a type tag plus an id. Deliberately
*not* a foreign key, because it would point at a different table per domain.

```java
@Enumerated(EnumType.STRING)
private PaymentReferenceType referenceType;   // ORDER  (CINEMA_BOOKING if ever needed)
private Long referenceId;                     // Order#42
```

`PaymentReferenceType` lives in `payment/`. That's a **name-only coupling** — `payment`
knows the string `"ORDER"` exists, but never imports the `Order` class.

Enum rather than free-form `String`: typo safety, and the set of known consumers stays
greppable in one place.

**Indexes:** `stripePaymentIntentId` unique; `(referenceType, referenceId)` indexed.

The same `referenceType` / `referenceId` / `userId` triple is stamped into Stripe
`metadata` on every object we create. That is how you get from a row in the Stripe
dashboard back to our database at 2am during an incident, and it costs one line.

---

## 7. One `Payment` row per attempt

`Order` used to hold `stripePaymentIntentId` directly. That column was unique and
singular, so a retry after a decline would create a second PaymentIntent in Stripe while
overwriting the reference to the first — the failed attempt vanishes.

Instead: **`Payment` is an attempt log.** One row per PaymentIntent ever created. An
Order with two declines and a success has three `Payment` rows. This is what makes the
`FAILED → PENDING` retry transition safe, and it's the audit trail you want during an
incident, a dispute, or a chargeback investigation.

---

## 8. Two vocabularies, two state machines

`PaymentStatus` mirrors **Stripe's own** PaymentIntent vocabulary — `REQUIRES_PAYMENT_METHOD`,
`REQUIRES_CONFIRMATION`, `REQUIRES_ACTION`, `PROCESSING`, `REQUIRES_CAPTURE`, `SUCCEEDED`,
`CANCELED` — rather than inventing names. When Stripe's docs say `requires_capture`, our
enum says the same thing, and the mapping is trivial to verify.

`OrderStatus` is *domain* vocabulary — `PENDING`, `PAID`, `PARTIALLY_REFUNDED`, `REFUNDED`,
`DISPUTED`, `CANCELLED`, `FAILED`. It answers "what should the business do about this
order", which is a different question from "what is the PaymentIntent doing".

Keeping the two separate is the point. **Translation happens at the boundary, once** — in
the event listener in `order/`. If Stripe adds a status next year, one mapping changes.

---

## 9. How a domain learns that payment succeeded

`payment` can't call `orderService.markPaid()` — that's the forbidden import. So it
publishes a Spring application event and lets whoever cares listen.

```
1. OrderService       → paymentService.createPaymentIntent(PaymentRequest)
                        (amount, currency, userId, referenceType=ORDER, referenceId=42)
2. PaymentService     → Stripe API, saves Payment row, returns client_secret
3. Customer           → completes payment client-side (3DS if required)
4. Stripe             → POST /webhooks/stripe
5. webhook/           → verify signature, dedup on stripeEventId, route by type
6. payment/ handler   → update Payment row, publish PaymentSucceededEvent
7. order/ listener    → filters referenceType == ORDER, order.transitionTo(PAID)
```

Step 7 uses `@TransactionalEventListener(phase = AFTER_COMMIT)` so the `Payment` row is
durable *before* the domain reacts.

**Alternative considered:** a `PaymentReferenceHandler` interface implemented per domain
and resolved from a `Map<PaymentReferenceType, Handler>`. More explicit, easier to trace
in a debugger, more boilerplate. Events won on stricter dependency inversion. Revisit if
the event flow gets hard to follow.

---

## 10. Webhooks are the source of truth

**The client redirect is a hint, not a fact.** The browser can close, the network can
drop, the user can bookmark the success URL and revisit it. Fulfilment happens on the
webhook, always. This is the single most common way real integrations lose money.

Two corollaries that are easy to get wrong:

- **`processing` is not `succeeded`.** ACH, SEPA and BNPL can sit in `processing` for
  days and then fail. Never fulfil an order on `processing`.
- **Return 2xx immediately, process asynchronously.** Stripe times out around 10s and
  retries; slow handlers create duplicate deliveries and a retry storm. Persist the raw
  event, return 200, then work.

### Keeping `webhook/` from becoming a god package

`webhook/` owns the *plumbing* only: signature verification, persistence, dedup, dispatch.
It defines an interface:

```java
public interface StripeEventHandler {
    boolean supports(String eventType);
    void handle(Event event);
}
```

Each domain implements it for its own events — `payment/` handles `payment_intent.*`,
`refund/` handles `charge.refunded` and `charge.dispute.*`, `billing/` handles `invoice.*`.
Spring injects them as `List<StripeEventHandler>` and the router picks matches.

Without this, `webhook` ends up importing every package in the project.

---

## 11. Drift is expected; reconciliation is the answer

If step 7 above fails after step 6 committed, `Payment` is `SUCCEEDED` while `Order` is
still `PENDING`. Webhooks also just get lost sometimes.

So there is a **scheduled reconciliation sweeper**: find `Payment` rows in a non-terminal
status older than N minutes, ask Stripe what actually happened, converge. This isn't
belt-and-braces paranoia — it's the standard design, and it's why the event listener is
allowed to be simple. The two features cover each other.

---

## 12. Migration from current code

| Change | File |
|---|---|
| Move `Order`, `OrderStatus`, `OrderRepository`, `InvalidOrderStatusTransitionException` from `payment/` → new `order/` package | 4 files |
| Drop `stripePaymentIntentId`, `stripeCheckoutSessionId` from `Order` (they move to `Payment`) | `order/Order.java` |
| Update `Order`/`OrderRepository` imports | `common/DataSeeder.java` |
| **Discard the draft `payment/OrderService.java`** — built on fields that no longer exist | — |

Already built and kept as-is:
- `Order.transitionTo()` state machine with `ALLOWED_TRANSITIONS`
- `@ExceptionHandler(Exception.class)` fallback in `GlobalExceptionHandler`
- Seeded test Order in `DataSeeder`

---

## 13. What we're not building, and why

| Product | Why not |
|---|---|
| **Issuing** | Issuing *spends* money on cards you hand out. It's a spend-management product with no relationship to accepting payment for an order. Interesting, but a different project. |
| **Terminal** | In-person card readers. Needs physical hardware to learn properly; the online surface is what transfers. |
| **Identity** | KYC document verification. Adjacent to Connect onboarding, not to order payments. |
| **Financial Connections** | Bank account linking. Mostly a prerequisite for ACH and Treasury; the ACH bits we need come through payment methods instead. |
| **Treasury** | Banking-as-a-service. Far outside order payments. |
| **Charges / Sources API** | Legacy. Superseded by PaymentIntents and doesn't support SCA. Worth *recognising* in old code, not worth building. |

If you finish the tracker and still want more, Issuing is the most self-contained of these
to bolt on afterwards.

---

## 14. Still open

- [ ] Lock down `Order.setStatus()` so `transitionTo()` is the only path (Lombok
      `@Setter` currently exposes both).
- [ ] Whether the event flow (§9) stays events or becomes the handler-map alternative.
      Decide after Phase 9, when there are enough listeners to judge.
- [ ] Whether `Payment` events should go through a transactional **outbox** table instead
      of Spring events. Correct answer for production; possibly more machinery than this
      project needs. Revisit if the reconciliation sweeper starts catching a lot.
- [ ] Frontend scope. A minimal static test page is unavoidable (3DS and the Payment
      Element can't be exercised from curl). Deciding not to go beyond that.

**Closed 2026-08-24 — no provider abstraction (Strategy over Stripe/PayPal/etc.) yet.**
A generic `PaymentProvider` interface can only expose what every provider shares, which
excludes the most valuable parts of Stripe (`client_secret` for 3DS, `requires_capture`,
`setup_future_usage`, Radar outcomes) — so it would hide exactly what this project exists
to learn. And an interface designed against one provider is Stripe-shaped by accident;
it'd be rewritten the day PayPal arrives. This is *orthogonal* to §2: domain-agnostic and
provider-agnostic are independent axes, and deferring the second doesn't weaken the first.
Keeping the option open costs nothing: no `com.stripe.*` types in `PaymentRequest` /
`PaymentResult`, only `PaymentService` touches `StripeClient`, and events carry our ids
rather than Stripe objects. The later refactor is then a rename plus an extract-interface.
Revisit when a second provider is actually being added.
