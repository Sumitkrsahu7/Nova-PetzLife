package com.novapetzlife.cart;

import com.novapetzlife.common.ApiResponse;
import com.novapetzlife.customer.Customer;
import com.novapetzlife.customer.CustomerService;
import com.novapetzlife.user.User;
import com.novapetzlife.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/cart")
@Tag(name = "Shopping Cart", description = "Session guest cart, customer cart, stock verification, and cart merging")
public class CartController {

    private final CartService cartService;
    private final CustomerService customerService;
    private final UserRepository userRepository;

    public CartController(CartService cartService, CustomerService customerService, UserRepository userRepository) {
        this.cartService = cartService;
        this.customerService = customerService;
        this.userRepository = userRepository;
    }

    private Customer resolveCustomer(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getName())) {
            User user = userRepository.findByEmail(authentication.getName().toLowerCase().trim()).orElse(null);
            if (user != null) {
                return customerService.getOrCreateCustomerForUser(user);
            }
        }
        return null;
    }

    @GetMapping
    @Operation(summary = "Get active shopping cart", description = "Retrieves active cart by session token or customer profile with live stock check and subtotals")
    public ResponseEntity<ApiResponse<CartDto>> getCart(
            Authentication authentication,
            @RequestHeader(value = "X-Session-Token", required = false) String headerToken,
            @RequestParam(required = false) String sessionToken) {

        String token = (headerToken != null && !headerToken.isBlank()) ? headerToken : sessionToken;
        Customer customer = resolveCustomer(authentication);
        Cart cart = cartService.getOrCreateActiveCart(customer, token);
        return ResponseEntity.ok(ApiResponse.ok(cartService.getCartDto(cart)));
    }

    @PostMapping("/items")
    @Operation(summary = "Add item to cart", description = "Adds product variant to cart after validating available stock")
    public ResponseEntity<ApiResponse<CartDto>> addItem(
            Authentication authentication,
            @RequestHeader(value = "X-Session-Token", required = false) String headerToken,
            @Valid @RequestBody AddToCartRequest request) {

        String token = (headerToken != null && !headerToken.isBlank()) ? headerToken : request.sessionToken();
        Customer customer = resolveCustomer(authentication);
        CartDto updated = cartService.addItem(customer, token, request);
        return ResponseEntity.ok(ApiResponse.ok("Item added to cart", updated));
    }

    @PutMapping("/items/{itemId}")
    @Operation(summary = "Update cart item quantity", description = "Updates quantity of an item; removing it if quantity is 0")
    public ResponseEntity<ApiResponse<CartDto>> updateQuantity(
            @PathVariable Long itemId,
            @Valid @RequestBody UpdateCartItemRequest request) {

        CartDto updated = cartService.updateItemQuantity(itemId, request.quantity());
        return ResponseEntity.ok(ApiResponse.ok("Cart updated", updated));
    }

    @DeleteMapping("/items/{itemId}")
    @Operation(summary = "Remove item from cart", description = "Deletes an item from the cart")
    public ResponseEntity<ApiResponse<CartDto>> removeItem(@PathVariable Long itemId) {
        CartDto updated = cartService.removeItem(itemId);
        return ResponseEntity.ok(ApiResponse.ok("Item removed from cart", updated));
    }

    @DeleteMapping("/clear")
    @Operation(summary = "Clear shopping cart", description = "Empties all items from the current active cart")
    public ResponseEntity<ApiResponse<Void>> clearCart(
            Authentication authentication,
            @RequestHeader(value = "X-Session-Token", required = false) String headerToken,
            @RequestParam(required = false) String sessionToken) {

        String token = (headerToken != null && !headerToken.isBlank()) ? headerToken : sessionToken;
        Customer customer = resolveCustomer(authentication);
        cartService.clearCart(customer, token);
        return ResponseEntity.ok(ApiResponse.ok("Cart cleared", null));
    }

    @PostMapping("/merge")
    @Operation(summary = "Merge guest cart into customer cart", description = "Merges temporary session items into authenticated customer cart on login")
    public ResponseEntity<ApiResponse<CartDto>> mergeCart(
            Authentication authentication,
            @RequestBody Map<String, String> body) {

        Customer customer = resolveCustomer(authentication);
        if (customer == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Authentication required to merge cart into account"));
        }

        String sessionToken = body.get("sessionToken");
        CartDto merged = cartService.mergeGuestCart(sessionToken, customer);
        return ResponseEntity.ok(ApiResponse.ok("Guest cart merged successfully", merged));
    }
}
