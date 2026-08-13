package com.lsg.mingler.domain.order.service;

import com.lsg.mingler.domain.order.dao.OrderRepository;
import com.lsg.mingler.domain.order.entity.Order;
import com.lsg.mingler.domain.order.entity.OrderStatus;
import com.lsg.mingler.domain.payment.dao.PaymentRepository;
import com.lsg.mingler.domain.payment.entity.Payment;
import com.lsg.mingler.domain.payment.entity.PaymentStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderExpirationTransactionServiceTest {

    private static final LocalDateTime CUTOFF = LocalDateTime.of(2026, 8, 2, 12, 0);

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private OrderExpirationTransactionService service;

    @Test
    void 결제시도가_없는_오래된_결제대기_주문을_만료한다() {
        Order order = order(OrderStatus.PENDING_PAYMENT, CUTOFF.minusMinutes(1));
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(paymentRepository.findAllByOrderIdOrderByCreatedAtDescIdDesc(1L)).thenReturn(List.of());

        boolean expired = service.expire(1L, CUTOFF, CUTOFF);

        assertThat(expired).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(order.getExpiredAt()).isNotNull();
    }

    @Test
    void 실패와_취소_결제만_있는_오래된_주문을_만료한다() {
        Order order = order(OrderStatus.PENDING_PAYMENT, CUTOFF.minusHours(1));
        Payment failed = payment(PaymentStatus.FAILED, CUTOFF.minusMinutes(20));
        Payment cancelled = payment(PaymentStatus.CANCELLED, CUTOFF.minusMinutes(10));
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(paymentRepository.findAllByOrderIdOrderByCreatedAtDescIdDesc(1L))
                .thenReturn(List.of(cancelled, failed));

        boolean expired = service.expire(1L, CUTOFF, CUTOFF);

        assertThat(expired).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRED);
    }

    @Test
    void 오래된_PENDING_결제를_취소하고_주문을_만료한다() {
        Order order = order(OrderStatus.PENDING_PAYMENT, CUTOFF.minusHours(1));
        Payment payment = payment(PaymentStatus.PENDING, CUTOFF.minusSeconds(1));
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(paymentRepository.findAllByOrderIdOrderByCreatedAtDescIdDesc(1L)).thenReturn(List.of(payment));

        boolean expired = service.expire(1L, CUTOFF, CUTOFF);

        assertThat(expired).isTrue();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(payment.getFailureCode()).isEqualTo("ORDER_EXPIRED");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRED);
    }

    @Test
    void 최근_PENDING_결제가_있으면_만료하지_않는다() {
        Order order = order(OrderStatus.PENDING_PAYMENT, CUTOFF.minusHours(1));
        Payment payment = payment(PaymentStatus.PENDING, CUTOFF);
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(paymentRepository.findAllByOrderIdOrderByCreatedAtDescIdDesc(1L)).thenReturn(List.of(payment));

        boolean expired = service.expire(1L, CUTOFF, CUTOFF);

        assertThat(expired).isFalse();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @ParameterizedTest
    @MethodSource("만료를_차단하는_결제상태")
    void 진행중_성공_환불계열_결제가_있으면_만료하지_않는다(PaymentStatus status) {
        Order order = order(OrderStatus.PENDING_PAYMENT, CUTOFF.minusHours(1));
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(paymentRepository.findAllByOrderIdOrderByCreatedAtDescIdDesc(1L))
                .thenReturn(List.of(payment(status, CUTOFF.minusHours(1))));

        assertThat(service.expire(1L, CUTOFF, CUTOFF)).isFalse();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    void 최신_주문이나_결제대기가_아닌_주문은_결제를_조회하지_않는다() {
        Order order = order(OrderStatus.PENDING_PAYMENT, CUTOFF);
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

        assertThat(service.expire(1L, CUTOFF, CUTOFF)).isFalse();
        verify(paymentRepository, never()).findAllByOrderIdOrderByCreatedAtDescIdDesc(1L);
    }

    private static Stream<PaymentStatus> 만료를_차단하는_결제상태() {
        return Stream.of(
                PaymentStatus.PROCESSING,
                PaymentStatus.SUCCESS,
                PaymentStatus.REFUND_PENDING,
                PaymentStatus.REFUNDED,
                PaymentStatus.REFUND_FAILED
        );
    }

    private Order order(OrderStatus status, LocalDateTime createdAt) {
        Order order = Order.builder()
                .orderNumber("ORD-1")
                .memberId(7L)
                .ordererName("주문자")
                .ordererPhone("01012345678")
                .receiverName("수령인")
                .receiverPhone("01012345678")
                .zipcode("12345")
                .address("서울")
                .merchandiseAmount(10_000)
                .totalAmount(10_000)
                .build();
        ReflectionTestUtils.setField(order, "id", 1L);
        ReflectionTestUtils.setField(order, "status", status);
        ReflectionTestUtils.setField(order, "createdAt", createdAt);
        return order;
    }

    private Payment payment(PaymentStatus status, LocalDateTime createdAt) {
        Payment payment = Payment.builder()
                .paymentNumber("PAY-" + status)
                .orderId(1L)
                .memberId(7L)
                .idempotencyKey("key-" + status)
                .pgProvider("VIRTUAL")
                .paymentMethod("CARD")
                .amount(10_000)
                .build();
        ReflectionTestUtils.setField(payment, "status", status);
        ReflectionTestUtils.setField(payment, "createdAt", createdAt);
        return payment;
    }
}
