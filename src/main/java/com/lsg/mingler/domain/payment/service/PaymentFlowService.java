package com.lsg.mingler.domain.payment.service;

import com.lsg.mingler.domain.payment.dao.PaymentRepository;
import com.lsg.mingler.domain.payment.dto.PaymentConfirmRequest;
import com.lsg.mingler.domain.payment.dto.PaymentConfirmResponse;
import com.lsg.mingler.domain.payment.entity.Payment;
import com.lsg.mingler.domain.payment.entity.PaymentStatus;
import com.lsg.mingler.global.error.AuthenticationException;
import com.lsg.mingler.global.error.PaymentApprovalException;
import com.lsg.mingler.global.error.PaymentGatewayUncertainException;
import com.lsg.mingler.global.validation.InputValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 결제 승인 준비, 외부 PG 호출, 성공·실패 확정과 결과 불명 재조정을 조율하는 애플리케이션 서비스다.
 */
@Service
@RequiredArgsConstructor
public class PaymentFlowService {

    private final PaymentProcessingTransactionService processingTransactionService;
    private final PaymentCompletionTransactionService completionTransactionService;
    private final PaymentOutcomeTransactionService outcomeTransactionService;
    private final PaymentAttemptQueryService queryService;
    private final PaymentReconciliationService reconciliationService;
    private final PaymentGatewayResolver gatewayResolver;
    private final PaymentRepository paymentRepository;

    /**
     * 요청과 소유권을 검증하고 재고 예약 트랜잭션과 외부 PG 승인 호출을 분리해 결제를 처리한다.
     *
     * @param memberId 인증된 회원 ID
     * @param guestOrderTokenHash 비회원 주문 토큰 해시
     * @param idempotencyKey 결제 준비부터 PG 승인까지 공유하는 멱등성 키
     * @param rawVirtualScenario 가상 결제에서 재현할 시나리오
     * @param request PG 인증 성공 결과와 승인 금액
     * @return 확정되었거나 처리 중인 결제와 주문 상태
     */
    public PaymentConfirmResponse confirm(Long memberId, String guestOrderTokenHash, String idempotencyKey, String rawVirtualScenario, PaymentConfirmRequest request) {
        validateAuthentication(memberId, guestOrderTokenHash);

        if (request == null) {
            throw new IllegalArgumentException("결제 승인 정보가 필요합니다.");
        }

        String normalizedKey = InputValidator.requireText(
                idempotencyKey, 100,
                "멱등성 키가 필요합니다.",
                "멱등성 키는 100자 이하여야 합니다.");
        String paymentKey = InputValidator.requireText(
                request.paymentKey(), 200,
                "PG 결제키가 필요합니다.",
                "PG 결제키는 200자 이하여야 합니다.");
        String pgOrderId = InputValidator.requireText(
                request.orderId(), 64,
                "PG 주문번호가 필요합니다.",
                "PG 주문번호는 64자 이하여야 합니다.");

        if (request.amount() == null || request.amount() <= 0) {
            throw new IllegalArgumentException("결제금액은 0원보다 커야 합니다.");
        }

        VirtualPaymentScenario scenario = "VIRTUAL".equals(gatewayResolver.currentProvider())
                ? VirtualPaymentScenario.from(rawVirtualScenario)
                : VirtualPaymentScenario.SUCCESS;

        PaymentProcessingContext context = processingTransactionService.start(
                memberId,
                guestOrderTokenHash,
                normalizedKey,
                paymentKey,
                pgOrderId,
                request.amount(),
                scenario);

        if (!context.requiresGatewayCall()) {
            if (context.response().paymentStatus() == PaymentStatus.PROCESSING) {
                PaymentConfirmResponse reconciled = reconciliationService.reconcile(pgOrderId);
                return reconciled == null
                        ? queryService.find(memberId, guestOrderTokenHash, pgOrderId)
                        : reconciled;
            }
            return context.response();
        }

        try {
            PaymentGateway gateway = gatewayResolver.require(context.provider());
            PaymentGatewayResult result = gateway.confirm(context.gatewayCommand());
            Payment payment = paymentRepository.findByPgOrderId(pgOrderId)
                    .orElseThrow(() -> new IllegalStateException("처리 중인 결제를 찾을 수 없습니다."));
            PaymentConfirmResponse applied = reconciliationService.applyResult(payment, result);
            return applied == null
                    ? queryService.find(memberId, guestOrderTokenHash, pgOrderId)
                    : applied;
        } catch (PaymentApprovalException failure) {
            outcomeTransactionService.finish(
                    pgOrderId,
                    PaymentStatus.FAILED,
                    failure.getFailureCode(),
                    failure.getMessage());
            throw failure;
        } catch (PaymentGatewayUncertainException uncertain) {
            return queryService.find(memberId, guestOrderTokenHash, pgOrderId);
        }
    }

    private void validateAuthentication(Long memberId, String guestOrderTokenHash) {
        if (memberId == null && guestOrderTokenHash == null) {
            throw new AuthenticationException("비회원 주문 조회 토큰이 필요합니다.");
        }
    }
}
