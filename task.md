# Stripe Playground — Task Tracker

Spring Boot 4.1.0 · Java 21 · Maven · stripe-java 33.1.1 · PostgreSQL · Spring Data JPA

Legend: `[ ]` not started · `[x]` done

> Architecture and the reasoning behind all of this live in **`plan.md`**.
> Read that first if anything here looks arbitrary.

### Scope
One domain: **Orders**. Everything Stripe does for accepting, settling, refunding and
disputing payment for an order. A second domain (cinema bookings) is **parked — no code**;
see `plan.md` §1. It exists only as the reason the architectural rule below is worth
obeying.

### The one architectural rule
`payment/` never imports a domain package. Domains import `payment/`. Nothing
Stripe-specific lives on `Order` any more.

### Cross-cutting conventions (apply in every phase)
- **Idempotency key on every mutating Stripe call**, derived from our own stable id — not
  a fresh UUID, which defeats the purpose on retry.
- **Stamp `referenceType` / `referenceId` / `userId` into Stripe `metadata`** on every
  object we create. This is how a dashboard row maps back to our DB during an incident.
- **Money is integer minor units + a currency code.** Never hardcode `/100` — JPY is
  zero-decimal, KWD is three-decimal. One formatting helper, nowhere else divides.
- **Webhooks are the source of truth**, never the client redirect. And `processing` is not
  `succeeded` — never fulfil on it.
- **Webhook handlers are state-convergent and never move a row backwards** out of a
  terminal status. Events arrive duplicated and out of order.
- **Log the Stripe request-id** on every call. It's the first thing support asks for.
- Stripe vocabulary stays in `payment/` (`PaymentStatus`); domain vocabulary stays in the
  domain (`OrderStatus`). Translate once, at the boundary.
- **Naming declares reusability.** No prefix (`Payment`, `Refund`, `TaxService`) = domain-
  constant, lives in its feature package. Domain prefix (`OrderInvoice`, `OrderTaxService`)
  = domain-specific, lives in **`order/`**, never in the feature package. Don't prefix
  defensively — an unprefixed name is a promise. See `plan.md` §2.
- No provider abstraction (Stripe only). Keep the seam anyway: no `com.stripe.*` in
  `PaymentRequest`/`PaymentResult`, only `PaymentService` touches `StripeClient`.
- Packages are created on demand, when the first file needs them — not all upfront.

### Reading the phase order
Phases **0–9 are the spine** — the complete lifecycle of money for an order, in dependency
order. Finish those and you can genuinely say you know Stripe payments.
Phases **10–16 are breadth** — real, commonly-needed, but each one is optional and they
can be reordered to taste once the spine is done.

Webhooks land at Phase 4, deliberately early: they're the source of truth, so every
feature built after them is webhook-driven from birth instead of retrofitted.

---

# THE SPINE

## Phase 0 — Foundations & Stripe wiring
- [x] Add `stripe-java` dependency to `pom.xml`
- [x] Convert `application.properties` → `application.yml` with Stripe test keys + PostgreSQL datasource
- [x] Create packages on demand (`common`, `user`, `product` exist so far)
- [x] Add `StripeConfig` bean (exposes injected `StripeClient`, not global static `Stripe.apiKey`)
- [x] Add base JPA entity (`id`, `createdAt`, `updatedAt`) for shared use
- [x] Add global exception handler skeleton
- [x] Replace global static `Stripe.apiKey` with an injected `StripeClient` bean
- [x] Pin the Stripe API version explicitly (locked via exact `stripe-java` version in `pom.xml`)
- [x] Map Stripe exceptions to API errors (`CardException`, `InvalidRequestException`, `RateLimitException`, `ApiConnectionException`, `AuthenticationException`)
- [x] Retry with backoff on transient failures (SDK built-in via `setMaxNetworkRetries`)
- [x] Generic `Exception` fallback handler so non-Stripe errors still return `ApiError`
- [x] `Money` helper: minor-unit arithmetic + currency exponent lookup + display formatting
- [x] Log the Stripe request-id from every response (success and failure)

## Phase 1 — Order domain (the thing being paid for)
- [x] `User` entity + repository
- [x] `Product` entity + repository
- [x] `Order` entity + repository
- [x] Order status state machine with strict transition rules
- [x] Seed data on startup (test Users + Products + one PENDING Order)
- [x] Move `Order`, `OrderStatus`, `OrderRepository`, `InvalidOrderStatusTransitionException` into a new `order/` package
- [x] Strip `stripePaymentIntentId` / `stripeCheckoutSessionId` off `Order` (they belong to `Payment`)
- [x] Lock down `setStatus` so `transitionTo()` is the only way to change status
- [x] Extend `OrderStatus` with `PARTIALLY_REFUNDED` and `DISPUTED`; update `ALLOWED_TRANSITIONS`
- [x] `OrderItem` entity — multi-line orders, **snapshotting unit price + currency at purchase time**
- [x] Order totals: `subtotal`, `discountAmount`, `shippingAmount`, `taxAmount`, `total` (all minor units)
- [x] `OrderService.create(...)` from products + quantities, computing totals
- [x] Order REST controller (create, get, list, cancel), testable via curl/Postman
- [x] Pessimistic locking (`SELECT FOR UPDATE`) on Order status updates
- [x] `ConflictException` base in `common/`; `InvalidOrderStatusTransitionException` extends it → `409` on GlobalExceptionHandler *(picked up mid-phase, not on the original list)*
- [ ] Listener: `PaymentSucceededEvent` / `PaymentFailedEvent` → `order.transitionTo(...)` — **blocked on Phase 3**, close out when Phase 3 publishes those events

## Phase 2 — Customers in Stripe
- [x] Create Stripe Customer when User is created
- [ ] Make Customer creation idempotent (never two Customers for one User)
- [ ] Store + backfill `stripeCustomerId` on `User`
- [ ] Customer address on `User` (required later for Stripe Tax)
- [ ] Update Stripe Customer when User email/name/address changes
- [ ] Stamp `userId` into Customer metadata
- [ ] Handle a deleted/missing Customer gracefully (recreate rather than 500)

## Phase 3 — Payment core: PaymentIntents
- [ ] `PaymentReferenceType` enum (`ORDER`)
- [ ] `PaymentStatus` enum mirroring Stripe's PaymentIntent statuses (incl. `REQUIRES_CAPTURE`)
- [ ] `Payment` entity + repository — one row per PaymentIntent attempt, never overwritten
- [ ] `PaymentRequest` / `PaymentResult` DTOs (the only vocabulary domains speak to `payment`)
- [ ] `PaymentService.createPaymentIntent(...)` with idempotency key + metadata
- [ ] Attach the Stripe Customer to the PaymentIntent
- [ ] `automatic_payment_methods` enabled (let Stripe decide what to show)
- [ ] Return `client_secret` to the caller
- [ ] `statement_descriptor` / `statement_descriptor_suffix` — what shows on the bank statement
- [ ] `receipt_email` for Stripe-hosted receipts
- [ ] Sync a `Payment` from Stripe on demand (retrieve + map status) — pre-webhook safety net
- [ ] Handle `requires_action` / 3D Secure end to end
- [ ] Publish `PaymentSucceededEvent` / `PaymentFailedEvent` (no domain imports)
- [ ] Minimal static test page with Stripe.js + Payment Element — **required**, 3DS cannot be exercised from curl
- [ ] Payment REST controllers, testable via curl/Postman
- [ ] Walk the full test-card catalogue: success, decline, insufficient funds, 3DS-required, 3DS-fail

## Phase 4 — Webhooks (closes the loop)
- [ ] `WebhookEvent` entity + repository (unique `stripeEventId`, raw payload stored)
- [ ] Single webhook controller endpoint reading the **raw request body** (not a parsed DTO)
- [ ] Stripe signature verification
- [ ] Event dedup (skip already-processed `stripeEventId`)
- [ ] Return 2xx immediately, process asynchronously (Stripe times out ~10s and retries)
- [ ] `StripeEventHandler` interface + router (handlers live in their own domain packages)
- [ ] Handle `payment_intent.succeeded`
- [ ] Handle `payment_intent.payment_failed`
- [ ] Handle `payment_intent.processing`
- [ ] Handle `payment_intent.requires_action`
- [ ] Handle `payment_intent.canceled`
- [ ] Handle `charge.succeeded` / `charge.updated` (this is where risk + balance data lives)
- [ ] Row locking on `Payment` updates from webhooks
- [ ] Out-of-order tolerance: never move a `Payment` backwards out of a terminal status
- [ ] Prove handler idempotency by replaying the same event twice
- [ ] Profile-switched signing secret (`stripe listen` prints a *different* `whsec_` than a dashboard endpoint)
- [ ] Local testing: `stripe listen --forward-to`, `stripe trigger`, `stripe events resend`
- [ ] Reconciliation sweeper: scheduled job polling Stripe for `Payment` rows stuck non-terminal
- [ ] Dead-letter / alert path for events that fail handling repeatedly

## Phase 5 — PaymentIntent lifecycle mastery
- [ ] Manual capture (`capture_method: manual`) — authorize now, capture later
- [ ] Capture in full
- [ ] Partial capture (capture less than authorized; the rest is released)
- [ ] Handle `payment_intent.amount_capturable_updated`
- [ ] Cancel a PaymentIntent, with `cancellation_reason`
- [ ] Wire Order cancellation to PaymentIntent cancellation (`CANCELLED` currently goes nowhere)
- [ ] Authorization expiry (~7 days) — detect and handle
- [ ] Update a PaymentIntent's amount before confirmation (cart changed)
- [ ] Retry after decline creates a **new** `Payment` row — failed attempts stay visible
- [ ] Map decline codes to user-facing messages (`card_declined`, `insufficient_funds`, `expired_card`, `incorrect_cvc`, `authentication_required`)
- [ ] Distinguish a *hard* decline (don't retry) from a *soft* one (retry may work)

## Phase 6 — Hosted flows: Checkout & Payment Links
- [ ] Checkout Session (hosted) built from Order line items
- [ ] Success / cancel URLs, and `session_id` in the return URL
- [ ] **Fulfil on `checkout.session.completed`, never on the redirect**
- [ ] Handle `checkout.session.async_payment_succeeded` / `.async_payment_failed`
- [ ] Handle `checkout.session.expired` and release the Order
- [ ] Embedded Checkout (same flow, mounted in your own page)
- [ ] Session expiry configuration
- [ ] Collect shipping address in Checkout
- [ ] Allow promotion codes in Checkout
- [ ] Adjustable quantity in Checkout
- [ ] Create a Payment Link programmatically and reconcile the resulting payment
- [ ] Compare: when is Checkout the right call vs a self-hosted PaymentIntent? Write it down.

## Phase 7 — Saved payment methods & off-session charging
- [ ] Save a card with **SetupIntent** (not bare `PaymentMethod.attach`)
- [ ] Save a card during a purchase via `setup_future_usage`
- [ ] Handle `setup_intent.succeeded` / `setup_intent.setup_failed`
- [ ] List saved payment methods for a Customer
- [ ] Set / change the Customer's default payment method
- [ ] Detach a saved payment method
- [ ] Show saved cards in the Payment Element via a Customer Session
- [ ] Charge off-session with a saved card (`off_session: true`, `confirm: true`)
- [ ] Handle the `authentication_required` off-session decline → recover on-session
- [ ] Mandate / agreement text — what you must show the customer before saving a card
- [ ] Handle `payment_method.attached` / `.detached` / `.automatically_updated`

## Phase 8 — Alternative payment methods & async settlement
- [ ] Let `automatic_payment_methods` surface non-card options
- [ ] Wallets: Apple Pay + Google Pay (incl. domain verification for Apple Pay)
- [ ] BNPL: Klarna and/or Afterpay
- [ ] Bank debits: ACH and/or SEPA — settle in **days**, not seconds
- [ ] Model the `processing` state on `Order` properly (not PAID, not FAILED)
- [ ] Handle a delayed failure days after `processing`
- [ ] Payment method availability by currency + country
- [ ] Multi-currency: presentment currency on the Order
- [ ] Prove the zero-decimal path works (charge in JPY, verify no `/100` bug)

## Phase 9 — Refunds
- [ ] `Refund` entity + repository (references `Payment`, not `Order`)
- [ ] Full refund on a succeeded `Payment`
- [ ] Partial refund
- [ ] Multiple partial refunds — track `refundedAmount`, reject over-refund
- [ ] Refund reasons (`duplicate`, `fraudulent`, `requested_by_customer`)
- [ ] Handle `refund.created` / `refund.updated` / `refund.failed`
- [ ] Handle `charge.refunded`
- [ ] Publish a refund event; Order → `PARTIALLY_REFUNDED` or `REFUNDED`
- [ ] Refunds are **not** always instant — model the pending state
- [ ] Refunding a payment whose original method is gone

---

# BREADTH

## Phase 10 — Disputes & Radar (fraud)
- [ ] `Dispute` entity + repository
- [ ] Handle `charge.dispute.created` → Order `DISPUTED`, funds already withdrawn
- [ ] Handle `charge.dispute.funds_withdrawn` / `.funds_reinstated`
- [ ] **Submit dispute evidence** via the API (the half that lets you actually win)
- [ ] Track the evidence deadline and surface it
- [ ] Handle `charge.dispute.closed` (won / lost) and settle the Order
- [ ] Extend `Payment` with `riskScore`, `riskLevel`, `sellerMessage`, `networkStatus`
- [ ] Read the Radar outcome off the Charge
- [ ] Flag high-risk payments for manual review; handle `review.opened` / `review.closed`
- [ ] Handle `radar.early_fraud_warning.created` (refund before it becomes a dispute)
- [ ] Document Radar rules + blocklists (dashboard-only, no code)
- [ ] Force a 3DS challenge with a Radar rule and observe the flow
- [ ] Trigger a real test dispute with the dispute test card

## Phase 11 — Discounts, shipping & tax
> Naming: `discount/` and `tax/` stay generic. Anything order-shaped is prefixed and lives
> in `order/` — `OrderPricingService`, `OrderTaxService`. Shipping is entirely order-shaped
> and gets no feature package.
- [ ] `discount/` — `Coupon` + `PromotionCode` in Stripe (generic)
- [ ] Percentage vs fixed-amount discounts; validity windows and redemption limits
- [ ] `order/OrderPricingService` — apply a discount to an Order's totals
- [ ] Shipping rates and shipping cost, in `order/`
- [ ] `taxCode` on `Product`; Stripe Tax registration setup (dashboard)
- [ ] `tax/` — Tax Calculation API wrapper (generic)
- [ ] `order/OrderTaxService` — decide which tax code applies, assemble the breakdown
- [ ] Automatic tax on Checkout Sessions
- [ ] Customer tax IDs and tax-exempt customers
- [ ] Order summary endpoint: subtotal → discount → shipping → tax → total, matching Stripe exactly
- [ ] Tax on refunds (refunding tax proportionally)

## Phase 12 — Money out: fees, balance & payouts
- [ ] Read the balance transaction for a charge → gross, Stripe fee, net
- [ ] Store `stripeFeeAmount` and `netAmount` on `Payment`
- [ ] Balance API: available vs pending
- [ ] Payouts to your own bank account; payout schedule
- [ ] Handle `payout.paid` / `payout.failed`
- [ ] Reconciliation report: our totals vs Stripe's balance transactions for a date range
- [ ] Currency conversion and settlement currency

## Phase 13 — Subscriptions (Billing)
- [ ] `Plan` / `Price` entity + repository
- [ ] `Subscription` entity + repository
- [ ] `Invoice` entity + repository
- [ ] Create subscription Products + Prices in Stripe
- [ ] Subscribe a User to a Plan with a payment method
- [ ] **Trial periods** + `customer.subscription.trial_will_end`
- [ ] Cancel immediately
- [ ] Cancel at period end
- [ ] Pause and resume
- [ ] Upgrade / downgrade with proration; preview the proration before committing
- [ ] Metered / usage-based billing
- [ ] SCA on renewals (`invoice.payment_action_required`)
- [ ] Dunning: smart retries, and what to do when they're exhausted
- [ ] Stripe Customer Portal session (hosted subscription management)
- [ ] Handle `invoice.created` / `.finalized` / `.paid` / `.payment_failed` / `.upcoming`
- [ ] Handle `customer.subscription.created` / `.updated` / `.deleted`
- [ ] **Test clocks** — fast-forward months to see renewals and dunning without waiting

## Phase 14 — Standalone invoicing & credit notes
- [ ] `ManualInvoice` entity + repository
- [ ] Draft invoice + invoice items
- [ ] Finalize and send
- [ ] Hosted invoice page + PDF
- [ ] Mark paid out of band
- [ ] Void an invoice
- [ ] Credit notes (full and partial)
- [ ] Handle `invoice.voided` / `invoice.marked_uncollectible`

## Phase 15 — Connect (marketplace orders)
- [ ] `Seller` entity + repository; `sellerId` on the Order path
- [ ] Create an Express connected account
- [ ] Onboarding `AccountLink`; handle the return/refresh URLs
- [ ] Handle `account.updated`; gate selling on `charges_enabled` / `payouts_enabled`
- [ ] Destination charge with `application_fee_amount`
- [ ] Separate charges and transfers
- [ ] Direct charges (`Stripe-Account` header) and how liability differs
- [ ] Transfer reversal
- [ ] Refund a split payment, including the application fee
- [ ] Payouts and payout schedules for connected accounts
- [ ] Connected-account webhook endpoint (events arrive with an `account` field)
- [ ] Write down who bears dispute liability under each charge type

## Phase 16 — Hardening, testing & ops
- [ ] Restricted API keys for the app; document rotation
- [ ] Secrets out of source control (env / vault), separate test and live config
- [ ] Never touch a raw card number — confirm PCI scope stays at SAQ-A
- [ ] Unit tests with a mocked `StripeClient`
- [ ] Integration tests against Stripe test mode
- [ ] Persist idempotency keys so a replayed request is provably safe
- [ ] Rate limit handling (`RateLimitException`) under load
- [ ] Consider a transactional **outbox** for payment events (see `plan.md` §14)
- [ ] Documented API version upgrade process
- [ ] Runbook: webhook backlog, stuck payments, mismatched totals

---

## Parked — not building (see `plan.md` §1 and §13)
- **Cinema ticket booking domain** (`Screening`, `Booking`) — the future second domain that
  would prove `payment/` is reusable. Would be: one entity, one `PaymentReferenceType`
  constant, one event listener, and **zero edits inside `payment/`**. Keep that true.
- **Issuing** (virtual cards) — spend management, not order payments. Best candidate if
  you want more after Phase 16.
- **Terminal** (in-person) — needs hardware.
- **Identity** (KYC) — belongs to Connect onboarding, not order payments.
- **Financial Connections**, **Treasury** — out of lane.
- **Charges / Sources API** — legacy, no SCA support. Recognise it in old code; don't build it.

---

### How we work
1. I only build the phase you explicitly tell me to start ("start phase X").
2. This is a teaching flow, not autopilot — I never write files myself.
   (Exception: tracking docs like `task.md` and `plan.md`, and any one-off you
   explicitly ask me to write.)
3. Within a phase, I give you **one small step at a time**:
   - The filename to create/edit (creating the package/folder it lives in too, if it doesn't exist yet).
   - The code to paste into it.
   - A brief explanation of why this code/package exists.
   - Packages are created on-demand, right when the first file needs them — not all upfront.
4. You paste the code yourself, read it, and confirm.
5. When you're ready, you say "give me next code" and I give you the next step — steps stay small, never bundled.
6. I don't check off any boxes mid-phase. Boxes are checked off together only once the **entire phase** is functionally complete.
