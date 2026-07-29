package com.lsg.mingler.domain.payment.service;

import com.lsg.mingler.domain.order.dao.OrderRepository;
import com.lsg.mingler.domain.order.entity.Order;
import com.lsg.mingler.domain.payment.dao.PaymentRepository;
import com.lsg.mingler.domain.payment.entity.Payment;
import com.lsg.mingler.domain.payment.entity.PaymentStatus;
import com.lsg.mingler.global.error.PaymentApprovalTimeoutException;
import com.lsg.mingler.global.error.PaymentDeclinedException;
import java.lang.reflect.Method;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentFailureTransactionServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentIdentifierGenerator identifierGenerator;

    @Captor
    private ArgumentCaptor<Payment> paymentCaptor;

    @InjectMocks
    private PaymentFailureTransactionService service;

    @Test
    void 승인거절은_독립된_실패결제로_기록한다() {
        Order order = memberOrder();
        when(orderRepository.findByOrderNumberForUpdate("ORD-1")).thenReturn(Optional.of(order));
        when(paymentRepository.findByMemberIdAndIdempotencyKey(7L, "key-1"))
                .thenReturn(Optional.empty());
        when(identifierGenerator.generatePaymentNumber()).thenReturn("PAY-FAILED-1");
        when(paymentRepository.existsByPaymentNumber("PAY-FAILED-1")).thenReturn(false);

        service.recordFailure(7L, null, command("key-1"), new PaymentDeclinedException());

        verify(paymentRepository).save(paymentCaptor.capture());
        Payment payment = paymentCaptor.getValue();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureCode()).isEqualTo("VIRTUAL_DECLINED");
        assertThat(payment.getFailureReason()).contains("거절");
        assertThat(payment.getFailedAt()).isNotNull();
        assertThat(payment.getPgTransactionKey()).isNull();
        assertThat(payment.getApprovedAt()).isNull();
    }

    @Test
    void 승인타임아웃은_전용_실패코드로_기록한다() {
        Order order = memberOrder();
        when(orderRepository.findByOrderNumberForUpdate("ORD-1")).thenReturn(Optional.of(order));
        when(paymentRepository.findByMemberIdAndIdempotencyKey(7L, "key-1"))
                .thenReturn(Optional.empty());
        when(identifierGenerator.generatePaymentNumber()).thenReturn("PAY-FAILED-1");
        when(paymentRepository.existsByPaymentNumber("PAY-FAILED-1")).thenReturn(false);

        service.recordFailure(
                7L,
                null,
                command("key-1"),
                new PaymentApprovalTimeoutException());

        verify(paymentRepository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getFailureCode())
                .isEqualTo("VIRTUAL_APPROVAL_TIMEOUT");
    }

    @Test
    void 동일한_멱등성키의_결제가_이미_있으면_실패이력을_중복저장하지_않는다() {
        Order order = memberOrder();
        Payment existing = Payment.builder()
                .paymentNumber("PAY-EXISTING")
                .orderId(500L)
                .memberId(7L)
                .idempotencyKey("key-1")
                .pgProvider("VIRTUAL")
                .paymentMethod("CARD")
                .amount(10_000)
                .build();
        when(orderRepository.findByOrderNumberForUpdate("ORD-1")).thenReturn(Optional.of(order));
        when(paymentRepository.findByMemberIdAndIdempotencyKey(7L, "key-1"))
                .thenReturn(Optional.of(existing));

        service.recordFailure(7L, null, command("key-1"), new PaymentDeclinedException());

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void 실패이력은_REQUIRES_NEW_트랜잭션으로_기록한다() throws Exception {
        Method method = PaymentFailureTransactionService.class.getDeclaredMethod(
                "recordFailure",
                Long.class,
                String.class,
                PaymentService.PaymentConfirmCommand.class,
                com.lsg.mingler.global.error.PaymentApprovalException.class);

        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }

    private PaymentService.PaymentConfirmCommand command(String idempotencyKey) {
        return new PaymentService.PaymentConfirmCommand(
                "ORD-1",
                10_000,
                "CARD",
                idempotencyKey,
                VirtualPaymentScenario.FAILED);
    }

    private Order memberOrder() {
        Order order = Order.builder()
                .orderNumber("ORD-1")
                .memberId(7L)
                .ordererName("주문자")
                .ordererPhone("010-1111-2222")
                .receiverName("수령인")
                .receiverPhone("010-1111-2222")
                .zipcode("12345")
                .address("서울")
                .merchandiseAmount(10_000)
                .discountAmount(0)
                .shippingFee(0)
                .totalAmount(10_000)
                .build();
        ReflectionTestUtils.setField(order, "id", 500L);
        return order;
    }
}
