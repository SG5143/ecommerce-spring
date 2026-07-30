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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 확정 실패 처리에서 결제 상태 변경과 예약 재고 복구가 함께 수행되는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class PaymentOutcomeTransactionServiceTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private PaymentInventoryService inventoryService;

    @InjectMocks
    private PaymentOutcomeTransactionService service;

    @Test
    void 확정실패는_예약재고를_복구하고_FAILED를_기록한다() {
        Payment payment = processingPayment();
        Order order = order();
        List<OrderItem> items = List.of(orderItem());
        when(paymentRepository.findByPgOrderId("TOSS-order")).thenReturn(Optional.of(payment));
        when(orderRepository.findByIdForUpdate(500L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByPgOrderIdForUpdate("TOSS-order")).thenReturn(Optional.of(payment));
        when(orderItemRepository.findAllByOrderIdOrderByIdAsc(500L)).thenReturn(items);

        service.finish(
                "TOSS-order",
                PaymentStatus.FAILED,
                "REJECT_CARD_PAYMENT",
                "카드 승인이 거절되었습니다.");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureCode()).isEqualTo("REJECT_CARD_PAYMENT");
        verify(inventoryService).restore(items);
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
