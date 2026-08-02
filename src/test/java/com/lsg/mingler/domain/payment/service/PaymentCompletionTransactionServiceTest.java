package com.lsg.mingler.domain.payment.service;

import com.lsg.mingler.domain.cart.dao.CartItemRepository;
import com.lsg.mingler.domain.order.dao.OrderItemRepository;
import com.lsg.mingler.domain.order.dao.OrderRepository;
import com.lsg.mingler.domain.order.entity.Order;
import com.lsg.mingler.domain.order.entity.OrderItem;
import com.lsg.mingler.domain.order.entity.OrderStatus;
import com.lsg.mingler.domain.payment.dao.PaymentRepository;
import com.lsg.mingler.domain.payment.dto.PaymentConfirmResponse;
import com.lsg.mingler.domain.payment.entity.Payment;
import com.lsg.mingler.domain.payment.entity.PaymentStatus;
import java.time.OffsetDateTime;
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
 * PG 승인 성공 결과의 무결성 검증과 주문·결제·장바구니의 단일 완료 처리를 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class PaymentCompletionTransactionServiceTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private CartItemRepository cartItemRepository;

    @InjectMocks
    private PaymentCompletionTransactionService service;

    @Test
    void DONE_응답은_주문과_결제를_성공시키고_주문장바구니만_삭제한다() {
        Payment payment = processingPayment();
        Order order = order();
        OrderItem item = orderItem(77L);
        stubLocked(payment, order);
        when(orderItemRepository.findAllByOrderIdOrderByIdAsc(500L)).thenReturn(List.of(item));

        PaymentConfirmResponse response = service.complete("TOSS-order", doneResult(20_000));

        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(cartItemRepository).deleteAllByIdInBatch(List.of(77L));
    }

    @Test
    void 승인금액이_다르면_PROCESSING과_재고예약을_유지한다() {
        Payment payment = processingPayment();
        Order order = order();
        stubLocked(payment, order);

        PaymentConfirmResponse response = service.complete("TOSS-order", doneResult(19_000));

        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(response.failureCode()).isEqualTo("MANUAL_REVIEW_REQUIRED");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        verify(cartItemRepository, never()).deleteAllByIdInBatch(org.mockito.ArgumentMatchers.anyList());
    }

    private void stubLocked(Payment payment, Order order) {
        when(paymentRepository.findByPgOrderId("TOSS-order")).thenReturn(Optional.of(payment));
        when(orderRepository.findByIdForUpdate(500L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByPgOrderIdForUpdate("TOSS-order")).thenReturn(Optional.of(payment));
    }

    private PaymentGatewayResult doneResult(int amount) {
        return new PaymentGatewayResult(
                "payment-key",
                "TOSS-order",
                amount,
                "DONE",
                "카드",
                "transaction-key",
                OffsetDateTime.now(),
                null,
                null);
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

    private OrderItem orderItem(Long sourceCartItemId) {
        return OrderItem.builder()
                .orderId(500L)
                .sourceCartItemId(sourceCartItemId)
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
