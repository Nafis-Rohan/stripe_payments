
// server
//    │
//    │ name = Nafis
//    │ email = nafis@gmail.com
//    │ userId = 25
//    ↓
//Stripe
//    │
//    │ creates customer
//    ↓
//cus_ABC123XYZ

package com.nafis.stripe_payments.user;


import com.nafis.stripe_payments.common.Address;
import com.nafis.stripe_payments.common.StripeLogging;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.net.RequestOptions;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.CustomerUpdateParams;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;



@Slf4j // Gives you a log object for logging.
@Service //Tells Spring this class contains business logic.
@RequiredArgsConstructor //Automatically creates a constructor for final fields.
public class UserService {

    private final UserRepository userRepository;
    private final StripeClient stripeClient;

    @Transactional(rollbackFor = StripeException.class)
    public User createUser(String name, String email) throws StripeException {
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user = userRepository.save(user);

        getOrCreateStripeCustomer(user);

        return user;
    }
    @Transactional(rollbackFor = StripeException.class)
    public User updateUser(Long userId, String name, String email,
                           Address address) throws StripeException {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));

        user.setName(name);
        user.setEmail(email);
        user.setAddress(address);
        // Flush now so updatedAt is populated before we read it below —it's
        // what makes the update's idempotency key stable across retries of
                // THIS attempt, but different from the next genuine update.
                user = userRepository.saveAndFlush(user);

        syncStripeCustomer(user);

        return user;
    }

    public User getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + id));
    }
    /**
     * Ensures the given User has a live Stripe Customer, creating one if
     * missing and recreating it if the existing one was deleted upstream.
     * Safe to call repeatedly — the idempotency key is derived from the
     * User's own id, so a retry never creates a duplicate Customer.
     */
    public Customer getOrCreateStripeCustomer(User user) throws StripeException {
        if (user.getStripeCustomerId() != null) {
            Customer existing = stripeClient.customers().retrieve(user.getStripeCustomerId());
            if (!Boolean.TRUE.equals(existing.getDeleted())) {
                return existing;
            }
            log.warn("Stripe customer {} for user {} was deleted upstream — recreating",
                    user.getStripeCustomerId(), user.getId());
        }

        CustomerCreateParams params = CustomerCreateParams.builder()
                .setName(user.getName())
                .setEmail(user.getEmail())
                .putMetadata("userId", String.valueOf(user.getId()))
                .build();

        RequestOptions requestOptions = RequestOptions.builder()
                .setIdempotencyKey("customer-create-user-" + user.getId())
                .build();

        Customer customer = stripeClient.customers().create(params, requestOptions);
        StripeLogging.logSuccess("customer.create", customer);

        user.setStripeCustomerId(customer.getId());
        userRepository.save(user);
        return customer;
    }
    private void syncStripeCustomer(User user) throws StripeException {
        Customer customer = getOrCreateStripeCustomer(user);

        CustomerUpdateParams.Builder paramsBuilder =
                CustomerUpdateParams.builder()
                        .setName(user.getName())
                        .setEmail(user.getEmail());

        if (user.getAddress() != null) {

            paramsBuilder.setAddress(CustomerUpdateParams.Address.builder()
                    .setLine1(user.getAddress().getLine1())
                    .setLine2(user.getAddress().getLine2())
                    .setCity(user.getAddress().getCity())
                    .setState(user.getAddress().getState())
                    .setPostalCode(user.getAddress().getPostalCode())
                    .setCountry(user.getAddress().getCountry())
                    .build());
        }

        RequestOptions requestOptions = RequestOptions.builder()
                .setIdempotencyKey("customer-update-user-" + user.getId()
                        + "-" + user.getUpdatedAt().toEpochMilli())
                .build();

        Customer updated = stripeClient.customers()
                .update(customer.getId(), paramsBuilder.build(),
                        requestOptions);
        StripeLogging.logSuccess("customer.update", updated);
    }
}


//
//
//@Slf4j // Gives you a log object for logging.
//@Service //Tells Spring this class contains business logic.
//@RequiredArgsConstructor //Automatically creates a constructor for final fields.
//// 1. Creates a new user in our local database first.
//// 2. Creates a matching customer in Stripe and links both accounts together.
//public class UserService {
//
//    private final UserRepository userRepository;
//    private final StripeClient stripeClient;
//
//    // Rolls back the User insert too if Stripe fails, so a retry with the
//    // same email starts clean instead of colliding with a half-created row.
//    @Transactional(rollbackFor = StripeException.class)
//    public User createUser(String name, String email) throws
//            StripeException {
//        User user = new User();
//        user.setName(name);
//        user.setEmail(email);
//        user = userRepository.save(user);
//
//        getOrCreateStripeCustomer(user);
//
//        return user;
//    }
//    /**
//     * Ensures the given User has a live Stripe Customer, creating one if
//     * missing and recreating it if the existing one was deleted upstream.
//     * Safe to call repeatedly — the idempotency key is derived from the
//     * User's own id, so a retry never creates a duplicate Customer.
//     */
//    public Customer getOrCreateStripeCustomer(User user) throws StripeException {
//        if (user.getStripeCustomerId() != null) {
//            Customer existing = stripeClient.customers().retrieve(user.getStripeCustomerId());
//            if (!Boolean.TRUE.equals(existing.getDeleted())) {
//                return existing;
//            }
//            log.warn("Stripe customer {} for user {} was deleted upstream — recreating",
//                    user.getStripeCustomerId(), user.getId());
//        }
//
//        CustomerCreateParams params = CustomerCreateParams.builder()
//                .setName(user.getName())
//                .setEmail(user.getEmail())
//                .putMetadata("userId", String.valueOf(user.getId()))
//                .build();
//
//
//        /**This creates additional options for the Stripe API request.
//         * it prevents accidental duplicate creation.
//         * when internet fails my server will not know is created or not
//         * so it will again give stripe create same userId (prevent it)*/
//        RequestOptions requestOptions = RequestOptions.builder()
//                .setIdempotencyKey("customer-create-user-" + user.getId())
//                .build();
//
//        Customer customer = stripeClient.customers().create(params, requestOptions);
//        StripeLogging.logSuccess("customer.create", customer);
//
//        user.setStripeCustomerId(customer.getId());
//        userRepository.save(user);
//        return customer;
//    }
//}
