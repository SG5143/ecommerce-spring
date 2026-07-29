package com.lsg.mingler.domain.payment.service;

import com.lsg.mingler.domain.payment.dto.PaymentConfirmRequest;
import com.lsg.mingler.domain.payment.dto.PaymentConfirmResponse;
import com.lsg.mingler.global.error.AuthenticationException;
import com.lsg.mingler.global.error.DuplicateException;
import com.lsg.mingler.global.error.PaymentApprovalException;
import com.lsg.mingler.global.util.HashUtils;
import com.lsg.mingler.global.validation.InputValidator;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private static final int MAX_ORDER_NUMBER_LENGTH = 50;
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;
    private static final String SUPPORTED_PAYMENT_METHOD = "CARD";

    private final PaymentRequestCoordinator requestCoordinator;
    private final PaymentTransactionService transactionService;
    private final PaymentFailureTransactionService failureTransactionService;

    /**
     * 결제 요청의 인증 정보와 입력값을 검증하고 멱등성 범위 안에서 승인 처리를 조율한다.
     *
     * @param memberId 인증된 회원 ID이며 비회원 요청이면 {@code null}
     * @param guestOrderTokenHash 비회원 주문 토큰 해시이며 회원 요청이면 {@code null}
     * @param idempotencyKey 중복 결제 방지를 위한 멱등성 키
     * @param rawScenario 선택한 가상 결제 시나리오이며 없으면 정상 승인
     * @param request 승인할 주문번호, 금액, 결제수단
     * @return 승인된 결제와 주문 상태
     * @throws AuthenticationException 회원 ID와 비회원 주문 토큰 해시가 모두 없는 경우
     * @throws IllegalArgumentException 요청값이 없거나 지원하지 않는 결제수단인 경우
     * @throws DuplicateException 이미 처리된 멱등성 키와 충돌하는 경우
     */
    public PaymentConfirmResponse confirm(Long memberId, String guestOrderTokenHash, String idempotencyKey, String rawScenario, PaymentConfirmRequest request) {
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
        VirtualPaymentScenario scenario = VirtualPaymentScenario.from(rawScenario);

        PaymentConfirmCommand command = new PaymentConfirmCommand(
                normalizedOrderNumber,
                request.amount(),
                normalizedPaymentMethod,
                normalizedIdempotencyKey,
                scenario);
        String ownerScope = memberId != null
                ? "MEMBER:" + memberId
                : "GUEST:" + guestOrderTokenHash + ":" + normalizedOrderNumber;
        String cacheKey = ownerScope + ":" + normalizedIdempotencyKey;
        String fingerprint = createFingerprint(command);

        try {
            return requestCoordinator.coordinate(
                    cacheKey,
                    fingerprint,
                    () -> confirmAndRecordFailure(memberId, guestOrderTokenHash, command));
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateException("이미 처리된 결제 요청입니다.");
        }
    }

    /**
     * 각 요청값을 길이 접두사 형식으로 정규화한 뒤 SHA-256 지문을 생성한다.
     *
     * @param command 정규화된 결제 승인 명령
     * @return 64자리 SHA-256 요청 지문
     */
    private String createFingerprint(PaymentConfirmCommand command) {
        String canonicalRequest = lengthPrefix(command.orderNumber())
                + lengthPrefix(command.amount().toString())
                + lengthPrefix(command.paymentMethod())
                + lengthPrefix(command.scenario().name());
        return HashUtils.sha256Hex(canonicalRequest);
    }

    private String lengthPrefix(String value) {
        return value.length() + ":" + value;
    }

    private PaymentConfirmResponse confirmAndRecordFailure(Long memberId, String guestOrderTokenHash, PaymentConfirmCommand command) {
        try {
            return transactionService.confirm(memberId, guestOrderTokenHash, command);
        } catch (PaymentApprovalException failure) {
            recordFailureSafely(memberId, guestOrderTokenHash, command, failure);
            throw failure;
        }
    }

    private void recordFailureSafely(Long memberId, String guestOrderTokenHash, PaymentConfirmCommand command, PaymentApprovalException failure) {
        try {
            failureTransactionService.recordFailure(memberId, guestOrderTokenHash, command, failure);
        } catch (DataIntegrityViolationException duplicateFailure) {
            log.info(
                    "동일한 멱등성 키의 실패 이력이 이미 존재합니다. (멱등성 키 중복). orderNumber={}, failureCode={}",
                    command.orderNumber(),
                    failure.getFailureCode());
        } catch (RuntimeException auditFailure) {
            log.error(
                    "결제 실패 이력을 기록하지 못했습니다. orderNumber={}, failureCode={}",
                    command.orderNumber(),
                    failure.getFailureCode(),
                    auditFailure);
        }
    }

    record PaymentConfirmCommand(
            String orderNumber,
            Integer amount,
            String paymentMethod,
            String idempotencyKey,
            VirtualPaymentScenario scenario
    ) {
    }
}
