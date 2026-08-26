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
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final StripeClient stripeClient;

    //@Transactional -> Run these database operations as ONE unit. Either all succeed, or all are rolled back
    @Transactional(rollbackFor = StripeException.class)
    public PaymentResult createPaymentIntent(PaymentRequest request)
            throws StripeException {
        Payment payment = new Payment();
        payment.setUserId(request.userId());
        payment.setReferenceType(request.referenceType());
        payment.setReferenceId(request.referenceId());
        payment.setAmount(request.amount());
        payment.setCurrency(request.currency());
        payment = paymentRepository.saveAndFlush(payment);

        PaymentIntentCreateParams params =
                PaymentIntentCreateParams.builder()
                        .setAmount(request.amount())
                        .setCurrency(request.currency())
                        .setCustomer(request.stripeCustomerId())
                        .setAutomaticPaymentMethods(

                                PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                        .setEnabled(true)
                                        .build())
                        .putMetadata("referenceType",
                                request.referenceType().name())
                        .putMetadata("referenceId",
                                String.valueOf(request.referenceId()))
                        .putMetadata("userId",
                                String.valueOf(request.userId()))
                        .build();
        RequestOptions requestOptions = RequestOptions.builder()
                .setIdempotencyKey("payment-" + payment.getId() + "-create")
                .build();

        PaymentIntent intent = stripeClient.paymentIntents().create(params, requestOptions);
        StripeLogging.logSuccess("paymentIntent.create", intent);

        payment.setStripePaymentIntentId(intent.getId());
        payment.setStatus(mapStatus(intent.getStatus()));
        paymentRepository.save(payment);

        return new PaymentResult(payment.getId(), intent.getId(), intent.getClientSecret(), payment.getStatus());
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
}
