package com.lsg.mingler.domain.payment.api;

import com.lsg.mingler.domain.payment.dto.PaymentConfirmRequest;
import com.lsg.mingler.domain.payment.dto.PaymentConfirmResponse;
import com.lsg.mingler.domain.payment.dto.PaymentPrepareRequest;
import com.lsg.mingler.domain.payment.dto.PaymentPrepareResponse;
import com.lsg.mingler.domain.payment.entity.PaymentStatus;
import com.lsg.mingler.domain.payment.service.PaymentAttemptQueryService;
import com.lsg.mingler.domain.payment.service.PaymentFlowService;
import com.lsg.mingler.domain.payment.service.PaymentPreparationCancelService;
import com.lsg.mingler.domain.payment.service.PaymentPreparationService;
import com.lsg.mingler.global.util.HashUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    public static final String VIRTUAL_PAYMENT_SCENARIO_HEADER = "X-Virtual-Payment-Scenario";

    private final PaymentPreparationService preparationService;
    private final PaymentFlowService paymentFlowService;
    private final PaymentAttemptQueryService queryService;
    private final PaymentPreparationCancelService cancelService;

    /**
     * 회원 JWT 또는 비회원 주문 토큰으로 주문 소유권을 확인하고 결제 시도를 준비한다.
     * 결제 금액은 요청값이 아닌 주문 스냅샷으로 계산하며, 동일한 멱등성 키의 재요청에는
     * 기존 결제 준비 결과를 반환한다.
     *
     * @param memberId 인증된 회원 ID이며 비회원 요청에서는 비어 있을 수 있다
     * @param idempotencyKey 결제 준비 요청의 중복 처리를 막기 위한 멱등성 키
     * @param guestOrderToken 비회원 주문 소유권 확인용 원문 토큰
     * @param request 준비할 주문번호와 결제수단
     * @return 결제창 호출에 필요한 주문번호, 금액, 클라이언트 키 등의 준비 결과
     */
    @PostMapping("/prepare")
    public ResponseEntity<PaymentPrepareResponse> prepare(
            @AuthenticationPrincipal Long memberId,
            @RequestHeader(name = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @RequestHeader(name = GUEST_ORDER_TOKEN_HEADER, required = false) String guestOrderToken,
            @RequestBody PaymentPrepareRequest request) {
        String guestOrderTokenHash = memberId == null ? hash(guestOrderToken) : null;
        return ResponseEntity.ok(preparationService.prepare(
                memberId,
                guestOrderTokenHash,
                idempotencyKey,
                request));
    }

    /**
     * 회원 JWT 또는 비회원 주문 토큰으로 주문 소유권을 식별해 가상 결제를 승인한다.
     *
     * @param memberId 인증된 회원 ID이며 비회원 요청에서는 비어 있을 수 있다
     * @param idempotencyKey 중복 결제 방지를 위한 멱등성 키
     * @param guestOrderToken 비회원 주문 소유권 확인용 원문 토큰
     * @param virtualPaymentScenario 선택한 가상 결제 시나리오
     * @param request 승인할 주문번호, 금액, 결제수단
     * @return 승인된 결제와 주문 상태
     */
    @PostMapping("/confirm")
    public ResponseEntity<PaymentConfirmResponse> confirm(
            @AuthenticationPrincipal Long memberId,
            @RequestHeader(name = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @RequestHeader(name = GUEST_ORDER_TOKEN_HEADER, required = false) String guestOrderToken,
            @RequestHeader(name = VIRTUAL_PAYMENT_SCENARIO_HEADER, required = false) String virtualPaymentScenario,
            @RequestBody PaymentConfirmRequest request) {
        String guestOrderTokenHash = memberId == null ? hash(guestOrderToken) : null;
        PaymentConfirmResponse response = paymentFlowService.confirm(
                memberId,
                guestOrderTokenHash,
                idempotencyKey,
                virtualPaymentScenario,
                request);
        return response.paymentStatus() == PaymentStatus.PROCESSING
                ? ResponseEntity.accepted().body(response)
                : ResponseEntity.ok(response);
    }

    /**
     * 회원 JWT 또는 비회원 주문 토큰으로 소유권을 확인하고 결제 시도의 현재 상태를 조회한다.
     * 승인 결과가 불명확하거나 성공 페이지가 새로고침된 경우에도 저장된 결제·주문 상태를 반환한다.
     *
     * @param memberId 인증된 회원 ID이며 비회원 요청에서는 비어 있을 수 있다
     * @param guestOrderToken 비회원 주문 소유권 확인용 원문 토큰
     * @param orderId 결제 준비 시 발급한 PG 주문번호
     * @return 현재 결제 상태와 주문 상태
     */
    @GetMapping("/attempts/{orderId}")
    public ResponseEntity<PaymentConfirmResponse> status(
            @AuthenticationPrincipal Long memberId,
            @RequestHeader(name = GUEST_ORDER_TOKEN_HEADER, required = false) String guestOrderToken,
            @PathVariable String orderId) {
        String guestOrderTokenHash = memberId == null ? hash(guestOrderToken) : null;
        return ResponseEntity.ok(queryService.find(memberId, guestOrderTokenHash, orderId));
    }

    /**
     * 결제창 인증이 실패하거나 취소된 준비 건을 멱등하게 종료한다.
     * PENDING 결제는 CANCELLED로 전환하며, 이미 승인 처리 중인 PROCESSING 결제는 취소하지 않는다.
     *
     * @param memberId 인증된 회원 ID이며 비회원 요청에서는 비어 있을 수 있다
     * @param guestOrderToken 비회원 주문 소유권 확인용 원문 토큰
     * @param orderId 결제 준비 시 발급한 PG 주문번호
     * @return 취소 처리 후 결제 상태와 주문 상태
     */
    @PostMapping("/preparations/{orderId}/cancel")
    public ResponseEntity<PaymentConfirmResponse> cancelPreparation(
            @AuthenticationPrincipal Long memberId,
            @RequestHeader(name = GUEST_ORDER_TOKEN_HEADER, required = false) String guestOrderToken,
            @PathVariable String orderId) {
        String guestOrderTokenHash = memberId == null ? hash(guestOrderToken) : null;
        return ResponseEntity.ok(cancelService.cancel(memberId, guestOrderTokenHash, orderId));
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
