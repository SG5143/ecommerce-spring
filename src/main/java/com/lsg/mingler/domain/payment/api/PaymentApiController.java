package com.lsg.mingler.domain.payment.api;

import com.lsg.mingler.domain.payment.dto.PaymentConfirmRequest;
import com.lsg.mingler.domain.payment.dto.PaymentConfirmResponse;
import com.lsg.mingler.domain.payment.service.PaymentService;
import com.lsg.mingler.global.util.HashUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentApiController {

    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    public static final String GUEST_ORDER_TOKEN_HEADER = "X-Guest-Order-Token";

    private final PaymentService paymentService;

    /**
     * 회원 JWT 또는 비회원 주문 토큰으로 주문 소유권을 식별해 가상 결제를 승인한다.
     *
     * @param memberId 인증된 회원 ID이며 비회원 요청에서는 비어 있을 수 있다
     * @param idempotencyKey 중복 결제 방지를 위한 멱등성 키
     * @param guestOrderToken 비회원 주문 소유권 확인용 원문 토큰
     * @param request 승인할 주문번호, 금액, 결제수단
     * @return 승인된 결제와 주문 상태
     */
    @PostMapping("/confirm")
    public ResponseEntity<PaymentConfirmResponse> confirm(
            @AuthenticationPrincipal Long memberId,
            @RequestHeader(name = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @RequestHeader(name = GUEST_ORDER_TOKEN_HEADER, required = false) String guestOrderToken,
            @RequestBody PaymentConfirmRequest request) {
        String guestOrderTokenHash = memberId == null ? hash(guestOrderToken) : null;
        return ResponseEntity.ok(paymentService.confirm(memberId, guestOrderTokenHash, idempotencyKey, request));
    }

    /**
     * 비회원 주문 토큰을 저장값과 비교할 수 있도록 SHA-256 해시로 변환한다.
     *
     * @param rawToken 비회원 주문 토큰 원문
     * @return 토큰 해시, 토큰이 없으면 {@code null}
     */
    private String hash(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return null;
        }
        return HashUtils.sha256Hex(rawToken);
    }
}
