package com.nafis.stripe_payments.user;

import com.nafis.stripe_payments.common.Address;
import com.nafis.stripe_payments.user.dto.AddressRequest;
import com.nafis.stripe_payments.user.dto.CreateUserRequest;
import com.nafis.stripe_payments.user.dto.UpdateUserRequest;
import com.nafis.stripe_payments.user.dto.UserResponse;
import com.stripe.exception.StripeException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    public ResponseEntity<UserResponse> createUser(@RequestBody
                                                   CreateUserRequest request) throws StripeException {
        User user = userService.createUser(request.name(),
                request.email());
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(user));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(UserResponse.from(userService.getUser(id)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<UserResponse> updateUser(@PathVariable Long id,
                                                   @RequestBody
                                                   UpdateUserRequest request) throws StripeException {
        Address address = toAddress(request.address());
        User user = userService.updateUser(id, request.name(),
                request.email(), address);
        return ResponseEntity.ok(UserResponse.from(user));
    }
    private Address toAddress(AddressRequest r) {
        if (r == null) {
            return null;
        }
        Address address = new Address();
        address.setLine1(r.line1());
        address.setLine2(r.line2());
        address.setCity(r.city());
        address.setState(r.state());
        address.setPostalCode(r.postalCode());
        address.setCountry(r.country());
        return address;
    }
}
