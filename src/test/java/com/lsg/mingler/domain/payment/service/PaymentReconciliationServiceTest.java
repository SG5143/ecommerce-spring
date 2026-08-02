package com.lsg.mingler.domain.payment.service;

import com.lsg.mingler.domain.payment.dao.PaymentRepository;
import com.lsg.mingler.domain.payment.entity.Payment;
import com.lsg.mingler.domain.payment.entity.PaymentStatus;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 애플리케이션 재시작 후 PG 조회 결과에 따라 PROCESSING 결제가 성공 또는 실패로 복구되는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class PaymentReconciliationServiceTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentGatewayResolver gatewayResolver;
    @Mock
    private PaymentCompletionTransactionService completionTransactionService;
    @Mock
    private PaymentOutcomeTransactionService outcomeTransactionService;
    @Mock
    private PaymentProperties properties;
    @Mock
    private PaymentGateway gateway;

    @InjectMocks
    private PaymentReconciliationService service;

    @Test
    void 재시작후_조회에서_DONE이면_성공완료_트랜잭션으로_복구한다() {
        Payment payment = processingPayment();
        PaymentGatewayResult result = new PaymentGatewayResult(
                "payment-key", "TOSS-order", 20_000, "DONE", "카드",
                "transaction-key", OffsetDateTime.now(), null, null);
        when(paymentRepository.findByPgOrderId("TOSS-order")).thenReturn(Optional.of(payment));
        when(gatewayResolver.require("TOSS")).thenReturn(gateway);
        when(gateway.lookupByPaymentKey("payment-key")).thenReturn(result);

        service.reconcile("TOSS-order");

        verify(completionTransactionService).complete("TOSS-order", result);
    }

    @Test
    void 조회에서_ABORTED이면_확정실패와_재고복구로_전환한다() {
        Payment payment = processingPayment();
        PaymentGatewayResult result = new PaymentGatewayResult(
                "payment-key", "TOSS-order", 20_000, "ABORTED", "카드",
                null, null, "REJECT_CARD_PAYMENT", "승인이 거절되었습니다.");
        when(paymentRepository.findByPgOrderId("TOSS-order")).thenReturn(Optional.of(payment));
        when(gatewayResolver.require("TOSS")).thenReturn(gateway);
        when(gateway.lookupByPaymentKey("payment-key")).thenReturn(result);

        service.reconcile("TOSS-order");

        verify(outcomeTransactionService).finish(
                "TOSS-order",
                PaymentStatus.FAILED,
                "REJECT_CARD_PAYMENT",
                "승인이 거절되었습니다.");
    }

    private Payment processingPayment() {
        Payment payment = Payment.builder()
                .paymentNumber("PAY-1")
                .orderId(500L)
                .memberId(7L)
                .idempotencyKey("key-1")
                .pgProvider("TOSS")
                .paymentMethod("CARD")
                .amount(20_000)
                .build();
        payment.recordPreparation("TOSS-order");
        payment.recordPaymentKey("payment-key");
        payment.changeStatus(PaymentStatus.PROCESSING);
        return payment;
    }
}
