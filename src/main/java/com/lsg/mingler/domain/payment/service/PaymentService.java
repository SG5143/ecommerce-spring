package com.lsg.mingler.domain.payment.service;

import com.lsg.mingler.domain.payment.dto.PaymentConfirmRequest;
import com.lsg.mingler.domain.payment.dto.PaymentConfirmResponse;
import com.lsg.mingler.global.error.AuthenticationException;
import com.lsg.mingler.global.error.DuplicateException;
import com.lsg.mingler.global.validation.InputValidator;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final int MAX_ORDER_NUMBER_LENGTH = 50;
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;
    private static final String SUPPORTED_PAYMENT_METHOD = "CARD";

    private final PaymentRequestCoordinator requestCoordinator;
    private final PaymentTransactionService transactionService;

    /**
     * 결제 요청의 인증 정보와 입력값을 검증하고 멱등성 범위 안에서 승인 처리를 조율한다.
     *
     * @param memberId 인증된 회원 ID이며 비회원 요청이면 {@code null}
     * @param guestOrderTokenHash 비회원 주문 토큰 해시이며 회원 요청이면 {@code null}
     * @param idempotencyKey 중복 결제 방지를 위한 멱등성 키
     * @param request 승인할 주문번호, 금액, 결제수단
     * @return 승인된 결제와 주문 상태
     * @throws AuthenticationException 회원 ID와 비회원 주문 토큰 해시가 모두 없는 경우
     * @throws IllegalArgumentException 요청값이 없거나 지원하지 않는 결제수단인 경우
     * @throws DuplicateException 이미 처리된 멱등성 키와 충돌하는 경우
     */
    public PaymentConfirmResponse confirm(Long memberId, String guestOrderTokenHash, String idempotencyKey, PaymentConfirmRequest request) {
        if (memberId == null && guestOrderTokenHash == null) {
            throw new AuthenticationException("비회원 주문 조회 토큰이 필요합니다.");
        }
        if (request == null) {
            throw new IllegalArgumentException("결제 요청 정보가 필요합니다.");
        }

        String normalizedOrderNumber = InputValidator.requireText(
                request.orderNumber(),
                MAX_ORDER_NUMBER_LENGTH,
                "주문번호가 필요합니다.",
                "주문번호는 50자 이하여야 합니다.");
        String normalizedIdempotencyKey = InputValidator.requireText(
                idempotencyKey,
                MAX_IDEMPOTENCY_KEY_LENGTH,
                "멱등성 키가 필요합니다.",
                "멱등성 키는 100자 이하여야 합니다.");
        if (request.amount() == null || request.amount() <= 0) {
            throw new IllegalArgumentException("결제금액은 0원보다 커야 합니다.");
        }
        String normalizedPaymentMethod = InputValidator.requireText(
                request.paymentMethod(),
                30,
                "결제수단이 필요합니다.",
                "결제수단은 30자 이하여야 합니다.").toUpperCase(Locale.ROOT);
        if (!SUPPORTED_PAYMENT_METHOD.equals(normalizedPaymentMethod)) {
            throw new IllegalArgumentException("현재 CARD 결제수단만 지원합니다.");
        }

        PaymentConfirmCommand command = new PaymentConfirmCommand(
                normalizedOrderNumber,
                request.amount(),
                normalizedPaymentMethod,
                normalizedIdempotencyKey);
        String ownerScope = memberId != null
                ? "MEMBER:" + memberId
                : "GUEST:" + guestOrderTokenHash + ":" + normalizedOrderNumber;
        String cacheKey = ownerScope + ":" + normalizedIdempotencyKey;
        String fingerprint = normalizedOrderNumber + "\n"
                + request.amount() + "\n" + normalizedPaymentMethod;

        try {
            return requestCoordinator.coordinate(
                    cacheKey,
                    fingerprint,
                    () -> transactionService.confirm(memberId, guestOrderTokenHash, command));
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateException("이미 처리된 결제 요청입니다.");
        }
    }

    record PaymentConfirmCommand(
            String orderNumber,
            Integer amount,
            String paymentMethod,
            String idempotencyKey
    ) {
    }
}
