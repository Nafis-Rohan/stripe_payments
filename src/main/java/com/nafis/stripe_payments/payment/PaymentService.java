package com.nafis.stripe_payments.payment;

import com.nafis.stripe_payments.common.StripeLogging;
import com.nafis.stripe_payments.payment.dto.PaymentRequest;
import com.nafis.stripe_payments.payment.dto.PaymentResult;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;


@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final StripeClient stripeClient;
    private final TransactionTemplate txTemplate; // ManualTransaction, Spring Boot auto-configures this bean

    public PaymentResult createPaymentIntent(PaymentRequest request)
            throws StripeException {


        Payment payment = txTemplate.execute(status -> {
            Payment p = new Payment();
            p.setUserId(request.userId());
            p.setReferenceType(request.referenceType());
            p.setReferenceId(request.referenceId());
            p.setAmount(request.amount());
            p.setCurrency(request.currency());
            return paymentRepository.save(p);
        });
        Long paymentId = payment.getId();

        // --- No transaction: the Stripe call. ---
        PaymentIntent intent;
        try {
            intent = stripeClient.paymentIntents().create(
                    buildCreateParams(request),
                    RequestOptions.builder()
                            .setIdempotencyKey("payment-" + paymentId + "-create")
                            .build());
        } catch (StripeException e) {
            log.warn("paymentIntent.create failed: paymentId={}, stripeRequestId={}",
                    paymentId, e.getRequestId(), e);
            throw e;
        }
        StripeLogging.logSuccess("paymentIntent.create", intent);

        Payment updated = txTemplate.execute(status -> {
            Payment p = paymentRepository.findById(paymentId).orElseThrow();
            p.setStripePaymentIntentId(intent.getId());
            p.setStatus(mapStatus(intent.getStatus()));
            return paymentRepository.save(p);
        });
        return new PaymentResult(updated.getId(), intent.getId(),
                intent.getClientSecret(), updated.getStatus());
    }

    private PaymentIntentCreateParams buildCreateParams(PaymentRequest request) {
        PaymentIntentCreateParams.Builder params = PaymentIntentCreateParams.builder()
                        .setAmount(request.amount())
                        .setCurrency(request.currency())
                        .setCustomer(request.stripeCustomerId())
                        .setStatementDescriptorSuffix(statementDescriptorSuffix(request))
                        .setAutomaticPaymentMethods(
                                PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                        .setEnabled(true)
                                        .build())
                        .putMetadata("referenceType",
                                request.referenceType().name())
                        .putMetadata("referenceId",
                                String.valueOf(request.referenceId()))
                        .putMetadata("userId",
                                String.valueOf(request.userId()));

        if (request.receiptEmail() != null &&
                !request.receiptEmail().isBlank()) {
            params.setReceiptEmail(request.receiptEmail());
        }

        return params.build();
    }
    private PaymentStatus mapStatus(String stripeStatus) {
        return switch (stripeStatus) {
            case "requires_payment_method" -> PaymentStatus.REQUIRES_PAYMENT_METHOD;
            case "requires_confirmation" -> PaymentStatus.REQUIRES_CONFIRMATION;
            case "requires_action" -> PaymentStatus.REQUIRES_ACTION;
            case "processing" -> PaymentStatus.PROCESSING;
            case "requires_capture" -> PaymentStatus.REQUIRES_CAPTURE;
            case "succeeded" -> PaymentStatus.SUCCEEDED;
            case "canceled" -> PaymentStatus.CANCELED;
            default -> throw new IllegalStateException("Unknown PaymentIntent status: " + stripeStatus);
        };

    }

    /**
     * What the cardholder sees on their bank statement, appended to the account's
     * static prefix as "PREFIX* SUFFIX". Stripe rules: the whole line (prefix +
     * suffix) is capped at 22 chars, and none of  < > \ ' " *  are allowed — so
     * strip anything that isn't a letter, digit or space and keep it short.
     * Putting the order reference here stops a customer reporting the charge as
     * fraud because they don't recognise it.
     */
    private String statementDescriptorSuffix(PaymentRequest request) {
        String raw = request.referenceType().name() + " " + request.referenceId(); // e.g. "ORDER 42"
        String cleaned = raw.replaceAll("[^A-Za-z0-9 ]", "").trim();
        return cleaned.length() > 10 ? cleaned.substring(0, 10) : cleaned;
    }

    /**
     * Pre-webhook safety net: pull a PaymentIntent's current state straight from
     * Stripe and converge our row to it. Until Phase 4 wires webhooks, this is
     * the only way our DB finds out a payment succeeded or failed after
     * createPaymentIntent already returned. Stays useful afterwards as a manual
     * "what does Stripe actually think?" check during an incident.
     */
    public PaymentResult syncFromStripe(Long paymentId) throws StripeException {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("No payment with id " + paymentId));

        if (payment.getStripePaymentIntentId() == null) {
            throw new IllegalStateException(
                    "Payment " + paymentId + " has no PaymentIntent yet — nothing to sync");
        }
        // No transaction around the network call — same reasoning as createPaymentIntent.
                PaymentIntent intent = stripeClient.paymentIntents()
                .retrieve(payment.getStripePaymentIntentId());
        StripeLogging.logSuccess("paymentIntent.retrieve", intent);

        PaymentStatus stripeStatus = mapStatus(intent.getStatus());

        Payment updated = txTemplate.execute(status -> {
            Payment p =
                    paymentRepository.findById(paymentId).orElseThrow();
            // State-convergent, and never walk a row backwards out of a terminal
            // status (plan.md §5 / §8). A real Payment.transitionTo() guard comes
            // later; this inline check is enough for now.
            if (isTerminal(p.getStatus()) && p.getStatus() !=
                    stripeStatus) {
                log.warn("Refusing to move payment {} from terminal {} to {} (Stripe: {})",
                paymentId, p.getStatus(), stripeStatus, intent.getStatus());
                return p;
            }
            p.setStatus(stripeStatus);
            return paymentRepository.save(p);
        });

        return new PaymentResult(updated.getId(), updated.getStripePaymentIntentId(),
                intent.getClientSecret(), updated.getStatus());

    }

    private boolean isTerminal(PaymentStatus status) {
        return status == PaymentStatus.SUCCEEDED || status == PaymentStatus.CANCELED;
    }

}




//
//@Slf4j
//@Service //Create an object of this class and manage it as a Spring Bean.(later inject it to controller)
//@RequiredArgsConstructor
//public class PaymentService {
//
//    private final PaymentRepository paymentRepository;
//    private final StripeClient stripeClient;
//
//    //@Transactional -> Run these database operations as ONE unit. Either all succeed, or all are rolled back
//    @Transactional(rollbackFor = StripeException.class)
//    public PaymentResult createPaymentIntent(PaymentRequest request)
//            throws StripeException {
//        Payment payment = new Payment();
//        payment.setUserId(request.userId());
//        payment.setReferenceType(request.referenceType());
//        payment.setReferenceId(request.referenceId());
//        payment.setAmount(request.amount());
//        payment.setCurrency(request.currency());
//        payment = paymentRepository.saveAndFlush(payment);
//        // why saveAndFlush? Synchronize this change with the database NOW
//        //why ? i need payment.id before before generating the idempotency key.
//
//        /** "Metadata"
//         * .putMetadata(...): Metadata is essentially digital sticky
//         * notes attached to the payment. Stripe doesn't care what is in here,
//         * but it is incredibly useful for you. If you look at this payment in the
//         * Stripe dashboard later, you will see exactly which userId and referenceId
//         * (like an order number) this payment belongs to.*/
//        /** "PaymentIntentCreateParams "
//         * is a special container provided
//         * by the Stripe Java library to neatly
//         * package all your payment instructions before sending them to Stripe.*/
//        PaymentIntentCreateParams params =
//                PaymentIntentCreateParams.builder()
//                        .setAmount(request.amount())
//                        .setCurrency(request.currency())
//                        .setCustomer(request.stripeCustomerId())
//                        .setStatementDescriptorSuffix(statementDescriptorSuffix(request))
//                        .setAutomaticPaymentMethods( //Automatically determine which payment methods can be used for this PaymentIntent., it only allows what is enabled in the Dashboard, AND only what is eligible for the specific transaction (currency, location, device).
//                                PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
//                                        .setEnabled(true)
//                                        .build())
//                        .putMetadata("referenceType",
//                                request.referenceType().name())
//                        .putMetadata("referenceId",
//                                String.valueOf(request.referenceId()))
//                        .putMetadata("userId",
//                                String.valueOf(request.userId()))
//                        .build();
//        /**RequestOptions
//         * using RequestOptions to add a highly specific shipping instruction
//         * Aside from the Idempotency Key
//         * developers use RequestOptions to add other network-level instructions,
//         * Timeouts, Connect, apiKeys*/
//        RequestOptions requestOptions = RequestOptions.builder()
//                .setIdempotencyKey("payment-" + payment.getId() + "-create")
//                .build();
//
//        PaymentIntent intent = stripeClient.paymentIntents().create(params, requestOptions);
//        StripeLogging.logSuccess("paymentIntent.create", intent);
//
//        payment.setStripePaymentIntentId(intent.getId());
//        payment.setStatus(mapStatus(intent.getStatus()));
//        paymentRepository.save(payment);
//
//        return new PaymentResult(payment.getId(), intent.getId(), intent.getClientSecret(), payment.getStatus());
//    }
//
//    private PaymentStatus mapStatus(String stripeStatus) {
//        return switch (stripeStatus) {
//            case "requires_payment_method" -> PaymentStatus.REQUIRES_PAYMENT_METHOD;
//            case "requires_confirmation" -> PaymentStatus.REQUIRES_CONFIRMATION;
//            case "requires_action" -> PaymentStatus.REQUIRES_ACTION;
//            case "processing" -> PaymentStatus.PROCESSING;
//            case "requires_capture" -> PaymentStatus.REQUIRES_CAPTURE;
//            case "succeeded" -> PaymentStatus.SUCCEEDED;
//            case "canceled" -> PaymentStatus.CANCELED;
//            default -> throw new IllegalStateException("Unknown PaymentIntent status: " + stripeStatus);
//        };
//    }
//
//    /**
//     * What the cardholder sees on their bank statement, appended to the account's
//     * static prefix as "PREFIX* SUFFIX". Stripe rules: max 22 chars for the whole
//     * line (prefix + suffix), and none of < > \ ' " *  — so we strip anything
//     * that isn't a letter, digit or space and cap the length. Putting the order
//     * reference here is what stops a customer calling it fraud because they don't
//     * recognise the charge.
//     */
//    private String statementDescriptorSuffix(PaymentRequest request) {
//        String raw = request.referenceType().name() + " " + request.referenceId(); // e.g. "ORDER 42"
//        String cleaned = raw.replaceAll("[^A-Za-z0-9 ]", "").trim();
//        return cleaned.length() > 10 ? cleaned.substring(0, 10) : cleaned;
//    }
//}
