package com.lsg.mingler.domain.order.api;

import com.lsg.mingler.domain.cart.service.GuestCartTokenManager;
import com.lsg.mingler.domain.order.dto.OrderCreateRequest;
import com.lsg.mingler.domain.order.dto.OrderCreateResponse;
import com.lsg.mingler.domain.order.dto.OrderHistoryResponse;
import com.lsg.mingler.domain.order.service.OrderHistoryService;
import com.lsg.mingler.domain.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderApiController {

    private final OrderService orderService;
    private final OrderHistoryService orderHistoryService;
    private final GuestCartTokenManager guestCartTokenManager;

    /**
     * 로그인 회원 본인의 주문내역을 최신순으로 페이지 조회한다.
     */
    @GetMapping
    public ResponseEntity<OrderHistoryResponse> getHistory(
            @AuthenticationPrincipal Long memberId,
            @RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok(orderHistoryService.getHistory(memberId, page));
    }

    /**
     * 회원은 JWT principal로, 비회원은 장바구니 쿠키로 소유자를 식별해 결제 전 주문서를 생성한다.
     * 비회원 토큰 원문은 서비스에 전달하지 않고 컨트롤러 경계에서 해시로 변환한다.
     */
    @PostMapping
    public ResponseEntity<OrderCreateResponse> createOrder(
            @AuthenticationPrincipal Long memberId,
            @CookieValue(name = GuestCartTokenManager.COOKIE_NAME, required = false) String guestCartToken,
            @RequestBody OrderCreateRequest request) {
        String guestCartTokenHash = memberId == null ? guestCartTokenManager.hash(guestCartToken) : null;
        OrderCreateResponse response = orderService.createOrder(memberId, guestCartTokenHash, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
