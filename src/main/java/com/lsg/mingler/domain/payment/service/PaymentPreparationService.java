package com.lsg.mingler.domain.payment.service;

import com.lsg.mingler.domain.payment.dto.PaymentPrepareRequest;
import com.lsg.mingler.domain.payment.dto.PaymentPrepareResponse;
import com.lsg.mingler.global.error.AuthenticationException;
import com.lsg.mingler.global.error.DuplicateException;
import com.lsg.mingler.global.validation.InputValidator;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 결제 준비 요청의 인증 정보와 입력값을 검증하고 트랜잭션 서비스에 준비 생성을 위임한다.
 */
@Service
@RequiredArgsConstructor
public class PaymentPreparationService {

    private static final String SUPPORTED_METHOD = "CARD";

    private final PaymentPreparationTransactionService transactionService;
    private final PaymentGatewayResolver gatewayResolver;

    /**
     * CARD 결제 준비 요청을 검증하고 멱등한 준비 결과를 생성한다.
     *
     * @param memberId 인증된 회원 ID
     * @param guestOrderTokenHash 비회원 주문 토큰 해시
     * @param idempotencyKey 결제 준비 멱등성 키
     * @param request 주문번호와 결제수단
     * @return 결제창 호출에 필요한 서버 검증 완료 정보
     */
    public PaymentPrepareResponse prepare(Long memberId, String guestOrderTokenHash, String idempotencyKey, PaymentPrepareRequest request) {
        validateAuthentication(memberId, guestOrderTokenHash);

        if (request == null) {
            throw new IllegalArgumentException("결제 준비 정보가 필요합니다.");
        }

        String orderNumber = InputValidator.requireText(
                request.orderNumber(), 50,
                "주문번호가 필요합니다.",
                "주문번호는 50자 이하여야 합니다.");
        String normalizedKey = InputValidator.requireText(
                idempotencyKey, 100,
                "멱등성 키가 필요합니다.",
                "멱등성 키는 100자 이하여야 합니다.");
        String method = InputValidator.requireText(
                request.paymentMethod(), 30,
                "결제수단이 필요합니다.",
                "결제수단은 30자 이하여야 합니다.").toUpperCase(Locale.ROOT);

        if (!SUPPORTED_METHOD.equals(method)) {
            throw new IllegalArgumentException("현재 CARD 결제수단만 지원합니다.");
        }
        String provider = gatewayResolver.resolveAvailableProvider(request.paymentProvider());

        try {
            return transactionService.prepare(
                    memberId, guestOrderTokenHash, orderNumber, method, provider, normalizedKey);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateException("이미 처리된 결제 준비 요청입니다.");
        }
    }

    private void validateAuthentication(Long memberId, String guestOrderTokenHash) {
        if (memberId == null && guestOrderTokenHash == null) {
            throw new AuthenticationException("비회원 주문 조회 토큰이 필요합니다.");
        }
    }
}
