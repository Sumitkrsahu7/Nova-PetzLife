package com.novapetzlife.cart;

import com.novapetzlife.customer.Customer;
import com.novapetzlife.inventory.InventoryService;
import com.novapetzlife.product.Product;
import com.novapetzlife.product.ProductRepository;
import com.novapetzlife.product.ProductVariant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;

    public CartService(
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            ProductRepository productRepository,
            InventoryService inventoryService) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.inventoryService = inventoryService;
    }

    @Transactional
    public Cart getOrCreateActiveCart(Customer customer, String sessionToken) {
        if (customer != null && customer.getId() != null) {
            return cartRepository.findByCustomerIdAndIsActiveTrue(customer.getId())
                    .orElseGet(() -> cartRepository.save(new Cart(customer, sessionToken)));
        }

        if (sessionToken != null && !sessionToken.isBlank()) {
            return cartRepository.findBySessionTokenAndIsActiveTrue(sessionToken.trim())
                    .orElseGet(() -> cartRepository.save(new Cart(null, sessionToken.trim())));
        }

        String newToken = UUID.randomUUID().toString();
        return cartRepository.save(new Cart(null, newToken));
    }

    @Transactional(readOnly = true)
    public CartDto getCartDto(Cart cart) {
        List<CartItemDto> itemDtos = new ArrayList<>();
        if (cart.getItems() != null) {
            for (CartItem item : cart.getItems()) {
                Long varId = item.getVariant() != null ? item.getVariant().getId() : null;
                int availableStock = inventoryService.getAvailableStock(item.getProduct().getId(), varId);
                itemDtos.add(CartItemDto.fromEntity(item, availableStock));
            }
        }
        return CartDto.of(cart.getId(), cart.getSessionToken(), itemDtos);
    }

    @Transactional
    public CartDto addItem(Customer customer, String sessionToken, AddToCartRequest request) {
        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new IllegalArgumentException("Product not found with id: " + request.productId()));

        if (Boolean.TRUE.equals(product.getIsArchived()) || !Boolean.TRUE.equals(product.getIsActive())) {
            throw new IllegalArgumentException("Product is currently unavailable: " + product.getName());
        }

        ProductVariant variant = null;
        BigDecimal unitPrice = product.getDiscountedPrice() != null ? product.getDiscountedPrice() : product.getBasePrice();

        if (request.variantId() != null) {
            variant = product.getVariants().stream()
                    .filter(v -> v.getId().equals(request.variantId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Variant not found with id: " + request.variantId()));

            unitPrice = variant.getDiscountedPrice() != null ? variant.getDiscountedPrice() : variant.getPrice();
        }

        int availableStock = inventoryService.getAvailableStock(product.getId(), request.variantId());
        if (availableStock < request.quantity()) {
            throw new IllegalStateException(String.format("Insufficient stock for %s. Requested: %d, Available: %d",
                    product.getName(), request.quantity(), availableStock));
        }

        String token = (sessionToken != null && !sessionToken.isBlank()) ? sessionToken.trim() : request.sessionToken();
        Cart cart = getOrCreateActiveCart(customer, token);

        Optional<CartItem> existingItem = request.variantId() != null
                ? cartItemRepository.findByCartIdAndProductIdAndVariantId(cart.getId(), product.getId(), request.variantId())
                : cartItemRepository.findByCartIdAndProductIdAndVariantIsNull(cart.getId(), product.getId());

        if (existingItem.isPresent()) {
            CartItem item = existingItem.get();
            int newQuantity = item.getQuantity() + request.quantity();
            if (newQuantity > availableStock) {
                throw new IllegalStateException(String.format("Cannot add %d more. Available stock is %d, and you already have %d in your cart.",
                        request.quantity(), availableStock, item.getQuantity()));
            }
            item.setQuantity(newQuantity);
            item.setUnitPrice(unitPrice);
            cartItemRepository.save(item);
        } else {
            CartItem newItem = new CartItem(cart, product, variant, request.quantity(), unitPrice);
            cart.addItem(newItem);
            cartItemRepository.save(newItem);
        }

        return getCartDto(cart);
    }

    @Transactional
    public CartDto updateItemQuantity(Long cartItemId, int newQuantity) {
        CartItem item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new IllegalArgumentException("Cart item not found with id: " + cartItemId));

        Cart cart = item.getCart();

        if (newQuantity <= 0) {
            cart.removeItem(item);
            cartItemRepository.delete(item);
        } else {
            Long varId = item.getVariant() != null ? item.getVariant().getId() : null;
            int availableStock = inventoryService.getAvailableStock(item.getProduct().getId(), varId);
            if (newQuantity > availableStock) {
                throw new IllegalStateException(String.format("Requested quantity (%d) exceeds available stock (%d)", newQuantity, availableStock));
            }
            item.setQuantity(newQuantity);
            cartItemRepository.save(item);
        }

        return getCartDto(cart);
    }

    @Transactional
    public CartDto removeItem(Long cartItemId) {
        CartItem item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new IllegalArgumentException("Cart item not found with id: " + cartItemId));

        Cart cart = item.getCart();
        cart.removeItem(item);
        cartItemRepository.delete(item);

        return getCartDto(cart);
    }

    @Transactional
    public void clearCart(Customer customer, String sessionToken) {
        Cart cart = getOrCreateActiveCart(customer, sessionToken);
        cart.getItems().clear();
        cartRepository.save(cart);
    }

    @Transactional
    public CartDto mergeGuestCart(String sessionToken, Customer customer) {
        if (sessionToken == null || sessionToken.isBlank() || customer == null) {
            return null;
        }

        Optional<Cart> guestCartOpt = cartRepository.findBySessionTokenAndIsActiveTrue(sessionToken.trim());
        if (guestCartOpt.isEmpty() || guestCartOpt.get().getItems().isEmpty()) {
            return getCartDto(getOrCreateActiveCart(customer, null));
        }

        Cart guestCart = guestCartOpt.get();
        Cart customerCart = getOrCreateActiveCart(customer, null);

        for (CartItem guestItem : new ArrayList<>(guestCart.getItems())) {
            Long varId = guestItem.getVariant() != null ? guestItem.getVariant().getId() : null;
            Optional<CartItem> custItemOpt = varId != null
                    ? cartItemRepository.findByCartIdAndProductIdAndVariantId(customerCart.getId(), guestItem.getProduct().getId(), varId)
                    : cartItemRepository.findByCartIdAndProductIdAndVariantIsNull(customerCart.getId(), guestItem.getProduct().getId());

            int availableStock = inventoryService.getAvailableStock(guestItem.getProduct().getId(), varId);

            if (custItemOpt.isPresent()) {
                CartItem custItem = custItemOpt.get();
                int mergedQty = Math.min(custItem.getQuantity() + guestItem.getQuantity(), availableStock);
                custItem.setQuantity(mergedQty);
                cartItemRepository.save(custItem);
            } else {
                int qty = Math.min(guestItem.getQuantity(), availableStock);
                if (qty > 0) {
                    CartItem newItem = new CartItem(customerCart, guestItem.getProduct(), guestItem.getVariant(), qty, guestItem.getUnitPrice());
                    customerCart.addItem(newItem);
                    cartItemRepository.save(newItem);
                }
            }
        }

        guestCart.setIsActive(false);
        cartRepository.save(guestCart);

        return getCartDto(customerCart);
    }
}
