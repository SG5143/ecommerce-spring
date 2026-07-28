package com.lsg.mingler.domain.cart.api;

import com.lsg.mingler.domain.cart.dto.CartCountResponse;
import com.lsg.mingler.domain.cart.dto.CartItemQuantityUpdateRequest;
import com.lsg.mingler.domain.cart.dto.CartItemsAddRequest;
import com.lsg.mingler.domain.cart.dto.CartMergeResponse;
import com.lsg.mingler.domain.cart.dto.CartResponse;
import com.lsg.mingler.domain.cart.service.CartService;
import com.lsg.mingler.domain.cart.service.GuestCartTokenManager;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartApiController {

    private final CartService cartService;
    private final GuestCartTokenManager guestCartTokenManager;

    @GetMapping
    public ResponseEntity<CartResponse> getCart(
            @AuthenticationPrincipal Long memberId,
            @CookieValue(name = GuestCartTokenManager.COOKIE_NAME, required = false) String guestToken) {
        return ResponseEntity.ok(cartService.getCart(memberId, hash(guestToken)));
    }

    @GetMapping("/count")
    public ResponseEntity<CartCountResponse> getCartCount(
            @AuthenticationPrincipal Long memberId,
            @CookieValue(name = GuestCartTokenManager.COOKIE_NAME, required = false) String guestToken) {
        int totalQuantity = cartService.getCartCount(memberId, hash(guestToken));
        return ResponseEntity.ok(new CartCountResponse(totalQuantity));
    }

    @PostMapping("/items")
    public ResponseEntity<CartResponse> addItems(
            @AuthenticationPrincipal Long memberId,
            @CookieValue(name = GuestCartTokenManager.COOKIE_NAME, required = false) String guestToken,
            @RequestBody CartItemsAddRequest request,
            HttpServletResponse response) {
        String effectiveGuestToken = guestToken;
        if (memberId == null && (effectiveGuestToken == null || effectiveGuestToken.isBlank())) {
            effectiveGuestToken = guestCartTokenManager.generate();
        }
        CartResponse cart = cartService.addItems(memberId, hash(effectiveGuestToken), request);
        if (memberId == null) {
            guestCartTokenManager.issueCookie(response, effectiveGuestToken);
        }
        return ResponseEntity.ok(cart);
    }

    @PatchMapping("/items/{itemId}")
    public ResponseEntity<CartResponse> updateQuantity(
            @AuthenticationPrincipal Long memberId,
            @CookieValue(name = GuestCartTokenManager.COOKIE_NAME, required = false) String guestToken,
            @PathVariable Long itemId,
            @RequestBody CartItemQuantityUpdateRequest request,
            HttpServletResponse response) {
        CartResponse cart = cartService.updateQuantity(
                memberId, hash(guestToken), itemId, request.quantity());
        refreshGuestCookie(response, memberId, guestToken);
        return ResponseEntity.ok(cart);
    }

    @DeleteMapping("/items")
    public ResponseEntity<CartResponse> deleteItems(
            @AuthenticationPrincipal Long memberId,
            @CookieValue(name = GuestCartTokenManager.COOKIE_NAME, required = false) String guestToken,
            @RequestParam List<Long> ids,
            HttpServletResponse response) {
        CartResponse cart = cartService.deleteItems(memberId, hash(guestToken), ids);
        refreshGuestCookie(response, memberId, guestToken);
        return ResponseEntity.ok(cart);
    }

    @PostMapping("/merge")
    public ResponseEntity<CartMergeResponse> merge(
            @AuthenticationPrincipal Long memberId,
            @CookieValue(name = GuestCartTokenManager.COOKIE_NAME, required = false) String guestToken,
            HttpServletResponse response) {
        CartMergeResponse result = cartService.mergeGuestCart(memberId, hash(guestToken));
        if (guestToken != null && !guestToken.isBlank()) {
            guestCartTokenManager.expireCookie(response);
        }
        return ResponseEntity.ok(result);
    }

    private String hash(String guestToken) {
        return guestCartTokenManager.hash(guestToken);
    }

    private void refreshGuestCookie(HttpServletResponse response, Long memberId, String guestToken) {
        if (memberId == null && guestToken != null && !guestToken.isBlank()) {
            guestCartTokenManager.issueCookie(response, guestToken);
        }
    }
}
