package com.lsg.mingler.domain.payment.service;

import com.lsg.mingler.domain.payment.dao.PaymentRepository;
import com.lsg.mingler.domain.payment.dto.PaymentConfirmResponse;
import com.lsg.mingler.domain.payment.entity.Payment;
import com.lsg.mingler.domain.payment.entity.PaymentStatus;
import com.lsg.mingler.global.error.PaymentGatewayNotFoundException;
import com.lsg.mingler.global.error.PaymentGatewayUncertainException;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 승인 결과가 불명확해 PROCESSING으로 남은 결제를 PG에서 다시 조회하고 로컬 상태를 복구
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentReconciliationService {

    private final PaymentRepository paymentRepository;
    private final PaymentGatewayResolver gatewayResolver;
    private final PaymentCompletionTransactionService completionTransactionService;
    private final PaymentOutcomeTransactionService outcomeTransactionService;
    private final PaymentProperties properties;

    /**
     * 설정된 주기로 오래된 PROCESSING 결제를 제한된 건수만큼 조회해 재조정
     * 개별 결제의 실패가 다른 결제 처리에 영향을 주지 않도록 건별 예외를 격리한다.
     */
    @Scheduled(fixedDelayString = "${payment.reconciliation.fixed-delay:PT30S}")
    public void reconcileScheduled() {
        LocalDateTime cutoff = LocalDateTime.now()
                .minus(properties.reconciliation().processingAge());

        List<Payment> payments = paymentRepository
                .findByStatusAndProcessingAtBeforeAndFailureCodeIsNullOrderByProcessingAtAsc(
                        PaymentStatus.PROCESSING,
                        cutoff,
                        PageRequest.of(0, properties.reconciliation().batchSize()));

        for (Payment payment : payments) {
            try {
                reconcile(payment.getPgOrderId());
            } catch (RuntimeException e) {
                log.warn("결제 재조정에 실패했습니다. pgOrderId={}", payment.getPgOrderId(), e);
            }
        }
    }

    /**
     * PG 결제 키를 우선 조회하고 찾지 못하면 PG 주문번호로 다시 조회해 현재 결과를 반영
     *
     * @param pgOrderId 재조정할 PG 주문번호
     * @return 상태가 변경된 경우 결제 결과, 대상이 아니거나 아직 확정할 수 없으면 {@code null}
     */
    public PaymentConfirmResponse reconcile(String pgOrderId) {
        Payment payment = paymentRepository.findByPgOrderId(pgOrderId).orElse(null);

        if (payment == null || payment.getStatus() != PaymentStatus.PROCESSING) {
            return null;
        }

        PaymentGateway gateway = gatewayResolver.require(payment.getPgProvider());

        PaymentGatewayResult result = lookup(gateway, payment);

        if (result == null) {
            if (isExpired(payment)) {
                return outcomeTransactionService.finish(
                        pgOrderId,
                        PaymentStatus.FAILED,
                        "PAYMENT_RECONCILIATION_NOT_FOUND",
                        "승인 유효시간 안에 PG 결제 결과를 찾지 못했습니다.");
            }
            return null;
        }

        return applyResult(payment, result);
    }

    /**
     * PG 상태를 성공, 확정 실패, 취소, 대기 또는 수동 검토 상태로 분류해 알맞은 트랜잭션에 위임
     *
     * @param payment 재조정 중인 로컬 결제
     * @param result PG 조회 결과
     * @return 상태가 확정된 경우 결제 결과, 계속 기다려야 하면 {@code null}
     */
    PaymentConfirmResponse applyResult(Payment payment, PaymentGatewayResult result) {
        return switch (result.status()) {
            case "DONE" -> completionTransactionService.complete(payment.getPgOrderId(), result);
            case "ABORTED", "EXPIRED" -> outcomeTransactionService.finish(
                    payment.getPgOrderId(),
                    PaymentStatus.FAILED,
                    result.failureCode() == null ? result.status() : result.failureCode(),
                    result.failureMessage() == null ? "결제 승인이 완료되지 않았습니다." : result.failureMessage());
            case "CANCELED" -> outcomeTransactionService.finish(
                    payment.getPgOrderId(),
                    PaymentStatus.CANCELLED,
                    "TOSS_CANCELED",
                    "토스 결제가 취소되었습니다.");
            case "READY", "IN_PROGRESS" -> isExpired(payment)
                    ? outcomeTransactionService.finish(
                    payment.getPgOrderId(),
                    PaymentStatus.FAILED,
                    "PAYMENT_APPROVAL_EXPIRED",
                    "결제 승인 유효시간이 만료되었습니다.")
                    : null;
            default -> outcomeTransactionService.markManualReview(
                    payment.getPgOrderId(),
                    "지원하지 않는 PG 결제 상태입니다: " + result.status());
        };
    }

    private PaymentGatewayResult lookup(PaymentGateway gateway, Payment payment) {
        try {
            return gateway.lookupByPaymentKey(payment.getPgPaymentKey());
        } catch (PaymentGatewayNotFoundException firstNotFound) {
            try {
                return gateway.lookupByOrderId(payment.getPgOrderId());
            } catch (PaymentGatewayNotFoundException secondNotFound) {
                return null;
            }
        } catch (PaymentGatewayUncertainException e) {
            log.info(
                    "PG 결제 조회 결과가 불명확합니다. pgOrderId={}, code={}",
                    payment.getPgOrderId(),
                    e.getFailureCode());
            return null;
        }
    }

    private boolean isExpired(Payment payment) {
        return payment.getProcessingAt() != null
                && payment.getProcessingAt().isBefore(
                LocalDateTime.now().minus(properties.reconciliation().expiryGrace()));
    }
}
