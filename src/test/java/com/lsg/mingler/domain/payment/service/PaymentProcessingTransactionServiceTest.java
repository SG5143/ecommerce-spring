package com.lsg.mingler.domain.payment.service;

import com.lsg.mingler.domain.order.dao.OrderItemRepository;
import com.lsg.mingler.domain.order.dao.OrderRepository;
import com.lsg.mingler.domain.order.entity.Order;
import com.lsg.mingler.domain.order.entity.OrderItem;
import com.lsg.mingler.domain.payment.dao.PaymentRepository;
import com.lsg.mingler.domain.payment.entity.Payment;
import com.lsg.mingler.domain.payment.entity.PaymentStatus;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 승인 준비 트랜잭션이 재고를 한 번만 예약하고 중복 요청에도 PROCESSING 상태를 멱등하게 유지하는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class PaymentProcessingTransactionServiceTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private PaymentInventoryService inventoryService;

    @InjectMocks
    private PaymentProcessingTransactionService service;

    @Test
    void 승인준비는_재고를_한번_예약하고_PROCESSING을_저장한다() {
        Payment payment = preparedPayment();
        Order order = order();
        List<OrderItem> items = List.of(orderItem());
        stubLocked(payment, order, items);

        PaymentProcessingContext context = service.start(
                7L, null, "key-1", "payment-key", "TOSS-order", 20_000,
                VirtualPaymentScenario.SUCCESS);

        assertThat(context.requiresGatewayCall()).isTrue();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(payment.getPgPaymentKey()).isEqualTo("payment-key");
        verify(inventoryService).reserve(items);
    }

    @Test
    void PROCESSING_재요청은_재고를_다시_예약하지_않는다() {
        Payment payment = preparedPayment();
        payment.recordPaymentKey("payment-key");
        payment.changeStatus(PaymentStatus.PROCESSING);
        Order order = order();
        when(paymentRepository.findByPgOrderId("TOSS-order")).thenReturn(Optional.of(payment));
        when(orderRepository.findByIdForUpdate(500L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByPgOrderIdForUpdate("TOSS-order")).thenReturn(Optional.of(payment));

        PaymentProcessingContext context = service.start(
                7L, null, "key-1", "payment-key", "TOSS-order", 20_000,
                VirtualPaymentScenario.SUCCESS);

        assertThat(context.requiresGatewayCall()).isFalse();
        verify(inventoryService, never()).reserve(org.mockito.ArgumentMatchers.anyList());
    }

    private void stubLocked(Payment payment, Order order, List<OrderItem> items) {
        when(paymentRepository.findByPgOrderId("TOSS-order")).thenReturn(Optional.of(payment));
        when(orderRepository.findByIdForUpdate(500L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByPgOrderIdForUpdate("TOSS-order")).thenReturn(Optional.of(payment));
        when(orderItemRepository.findAllByOrderIdOrderByIdAsc(500L)).thenReturn(items);
    }

    private Payment preparedPayment() {
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
        return payment;
    }

    private Order order() {
        Order order = Order.builder()
                .orderNumber("ORD-1")
                .memberId(7L)
                .ordererName("주문자")
                .ordererPhone("01012345678")
                .receiverName("수령인")
                .receiverPhone("01012345678")
                .zipcode("12345")
                .address("서울")
                .merchandiseAmount(20_000)
                .discountAmount(0)
                .shippingFee(0)
                .totalAmount(20_000)
                .build();
        ReflectionTestUtils.setField(order, "id", 500L);
        return order;
    }

    private OrderItem orderItem() {
        return OrderItem.builder()
                .orderId(500L)
                .productId(10L)
                .productOptionId(20L)
                .productName("상품")
                .categoryId(1L)
                .categoryName("카테고리")
                .unitPrice(20_000)
                .quantity(1)
                .lineAmount(20_000)
                .build();
    }
}
