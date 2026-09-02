package com.nafis.stripe_payments.payment;


import com.nafis.stripe_payments.payment.dto.PaymentRequest;
import com.nafis.stripe_payments.payment.dto.PaymentResponse;
import com.nafis.stripe_payments.payment.dto.PaymentResult;
import com.stripe.exception.StripeException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * Create a PaymentIntent straight from raw params. In the real
     flow order/
     * calls PaymentService; this endpoint exists so the payment
     surface is
     * exercisable from curl / the test page without going through an
     Order.
     */
    @PostMapping
    public ResponseEntity<PaymentResult> create(@RequestBody PaymentRequest request)
            throws StripeException {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentService.createPaymentIntent(request));
    }

    /** Pull the latest state from Stripe and converge our row
     (pre-webhook safety net). */
    @PostMapping("/{id}/sync")
    public ResponseEntity<PaymentResult> sync(@PathVariable Long id)
            throws StripeException {
        return ResponseEntity.ok(paymentService.syncFromStripe(id));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> get(@PathVariable Long id)
    {
        return ResponseEntity.ok(PaymentResponse.from(paymentService.
                getPayment(id)));
    }
}
