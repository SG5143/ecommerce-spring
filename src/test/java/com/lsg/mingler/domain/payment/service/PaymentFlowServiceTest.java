package com.lsg.mingler.domain.payment.service;

import com.lsg.mingler.domain.order.entity.OrderStatus;
import com.lsg.mingler.domain.payment.dao.PaymentRepository;
import com.lsg.mingler.domain.payment.dto.PaymentConfirmRequest;
import com.lsg.mingler.domain.payment.dto.PaymentConfirmResponse;
import com.lsg.mingler.domain.payment.entity.Payment;
import com.lsg.mingler.domain.payment.entity.PaymentStatus;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentFlowServiceTest {

    @Mock
    private PaymentProcessingTransactionService processingTransactionService;
    @Mock
    private PaymentCompletionTransactionService completionTransactionService;
    @Mock
    private PaymentOutcomeTransactionService outcomeTransactionService;
    @Mock
    private PaymentAttemptQueryService queryService;
    @Mock
    private PaymentReconciliationService reconciliationService;
    @Mock
    private PaymentGatewayResolver gatewayResolver;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentGateway gateway;

    @InjectMocks
    private PaymentFlowService service;

    @Test
    void 저장된_VIRTUAL_제공자로_가상결제를_승인하고_완료결과를_반환한다() {
        PaymentConfirmRequest request = new PaymentConfirmRequest(
                "VIRTUAL-payment-key", "VIRTUAL-order", 10_000);
        PaymentGatewayCommand command = new PaymentGatewayCommand(
                "VIRTUAL-payment-key",
                "VIRTUAL-order",
                10_000,
                "key-1",
                VirtualPaymentScenario.SUCCESS);
        PaymentProcessingContext context = new PaymentProcessingContext(
                processingResponse(),
                command,
                "VIRTUAL",
                true);
        Payment payment = processingPayment();
        PaymentGatewayResult gatewayResult = new PaymentGatewayResult(
                "VIRTUAL-payment-key",
                "VIRTUAL-order",
                10_000,
                "DONE",
                "카드",
                "VPG-VIRTUAL-order",
                OffsetDateTime.now(),
                null,
                null);
        PaymentConfirmResponse expected = successResponse();
        when(processingTransactionService.start(
                7L, null, "key-1", "VIRTUAL-payment-key", "VIRTUAL-order", 10_000, null))
                .thenReturn(context);
        when(gatewayResolver.require("VIRTUAL")).thenReturn(gateway);
        when(gateway.confirm(command)).thenReturn(gatewayResult);
        when(paymentRepository.findByPgOrderId("VIRTUAL-order")).thenReturn(Optional.of(payment));
        when(reconciliationService.applyResult(payment, gatewayResult)).thenReturn(expected);

        PaymentConfirmResponse response = service.confirm(
                7L, null, "key-1", null, request);

        assertThat(response).isEqualTo(expected);
        verify(gatewayResolver).require("VIRTUAL");
    }

    private Payment processingPayment() {
        Payment payment = Payment.builder()
                .paymentNumber("PAY-1")
                .orderId(500L)
                .memberId(7L)
                .idempotencyKey("key-1")
                .pgProvider("VIRTUAL")
                .paymentMethod("CARD")
                .amount(10_000)
                .build();
        payment.recordPreparation("VIRTUAL-order");
        payment.recordPaymentKey("VIRTUAL-payment-key");
        payment.changeStatus(PaymentStatus.PROCESSING);
        return payment;
    }

    private PaymentConfirmResponse processingResponse() {
        return new PaymentConfirmResponse(
                "PAY-1",
                "ORD-1",
                PaymentStatus.PROCESSING,
                OrderStatus.PENDING_PAYMENT,
                10_000,
                "CARD",
                "VIRTUAL",
                null,
                null);
    }

    private PaymentConfirmResponse successResponse() {
        return new PaymentConfirmResponse(
                "PAY-1",
                "ORD-1",
                PaymentStatus.SUCCESS,
                OrderStatus.PAID,
                10_000,
                "CARD",
                "VIRTUAL",
                "VPG-VIRTUAL-order",
                null);
    }
}
