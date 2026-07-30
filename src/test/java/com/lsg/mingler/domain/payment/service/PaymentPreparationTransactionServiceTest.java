package com.lsg.mingler.domain.payment.service;

import com.lsg.mingler.domain.order.dao.OrderItemRepository;
import com.lsg.mingler.domain.order.dao.OrderRepository;
import com.lsg.mingler.domain.order.entity.Order;
import com.lsg.mingler.domain.order.entity.OrderItem;
import com.lsg.mingler.domain.payment.dao.PaymentRepository;
import com.lsg.mingler.domain.payment.dto.PaymentPrepareResponse;
import com.lsg.mingler.domain.payment.entity.Payment;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 결제 준비가 서버 주문 스냅샷을 사용하고 동일 멱등성 키에 같은 준비 결과를 반환하는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class PaymentPreparationTransactionServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentIdentifierGenerator identifierGenerator;
    @Mock
    private PaymentGatewayResolver gatewayResolver;
    @Mock
    private PaymentProperties properties;

    @InjectMocks
    private PaymentPreparationTransactionService service;

    @Test
    void 결제준비는_주문스냅샷_금액으로_PENDING을_저장한다() {
        Order order = order();
        OrderItem item = orderItem();
        when(orderRepository.findByOrderNumberForUpdate("ORD-1")).thenReturn(Optional.of(order));
        when(paymentRepository.findByMemberIdAndIdempotencyKey(7L, "key-1"))
                .thenReturn(Optional.empty());
        when(paymentRepository.findFirstByOrderIdAndStatusInOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.eq(500L),
                org.mockito.ArgumentMatchers.any())).thenReturn(Optional.empty());
        when(orderItemRepository.findAllByOrderIdOrderByIdAsc(500L)).thenReturn(List.of(item));
        when(gatewayResolver.currentProvider()).thenReturn("TOSS");
        when(gatewayResolver.clientKey()).thenReturn("test_ck_key");
        when(identifierGenerator.generatePaymentNumber()).thenReturn("PAY-1");
        when(identifierGenerator.generatePgOrderId("TOSS")).thenReturn("TOSS-123456");
        when(identifierGenerator.customerKeyFrom("TOSS-123456")).thenReturn("customer-123456");

        PaymentPrepareResponse response = service.prepare(
                7L, null, "ORD-1", "CARD", "key-1");

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualTo(20_000);
        assertThat(captor.getValue().getPgOrderId()).isEqualTo("TOSS-123456");
        assertThat(response.amount()).isEqualTo(20_000);
        assertThat(response.orderName()).isEqualTo("테스트 상품");
        assertThat(response.clientKey()).isEqualTo("test_ck_key");
    }

    @Test
    void 동일_멱등키의_준비요청은_기존_PG주문번호를_반환한다() {
        Order order = order();
        OrderItem item = orderItem();
        Payment payment = Payment.builder()
                .paymentNumber("PAY-1")
                .orderId(500L)
                .memberId(7L)
                .idempotencyKey("key-1")
                .pgProvider("TOSS")
                .paymentMethod("CARD")
                .amount(20_000)
                .build();
        payment.recordPreparation("TOSS-existing");
        when(orderRepository.findByOrderNumberForUpdate("ORD-1")).thenReturn(Optional.of(order));
        when(paymentRepository.findByMemberIdAndIdempotencyKey(7L, "key-1"))
                .thenReturn(Optional.of(payment));
        when(orderItemRepository.findAllByOrderIdOrderByIdAsc(500L)).thenReturn(List.of(item));
        when(gatewayResolver.currentProvider()).thenReturn("TOSS");
        when(gatewayResolver.clientKey()).thenReturn("test_ck_key");
        when(identifierGenerator.customerKeyFrom("TOSS-existing")).thenReturn("customer-existing");

        PaymentPrepareResponse response = service.prepare(
                7L, null, "ORD-1", "CARD", "key-1");

        assertThat(response.orderId()).isEqualTo("TOSS-existing");
        assertThat(response.customerKey()).isEqualTo("customer-existing");
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
                .productName("테스트 상품")
                .categoryId(1L)
                .categoryName("카테고리")
                .unitPrice(20_000)
                .quantity(1)
                .lineAmount(20_000)
                .build();
    }
}
