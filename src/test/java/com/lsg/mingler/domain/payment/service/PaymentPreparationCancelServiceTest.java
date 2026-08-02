package com.lsg.mingler.domain.payment.service;

import com.lsg.mingler.domain.order.dao.OrderRepository;
import com.lsg.mingler.domain.order.entity.Order;
import com.lsg.mingler.domain.order.entity.OrderStatus;
import com.lsg.mingler.domain.payment.dao.PaymentRepository;
import com.lsg.mingler.domain.payment.dto.PaymentConfirmResponse;
import com.lsg.mingler.domain.payment.entity.Payment;
import com.lsg.mingler.domain.payment.entity.PaymentStatus;
import com.lsg.mingler.global.error.ResourceNotFoundException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentPreparationCancelServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private PaymentPreparationCancelService service;

    @Test
    void 결제준비_취소는_결제만_취소하고_주문은_재결제할_수_있게_유지한다() {
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
        Payment payment = Payment.builder()
                .paymentNumber("PAY-1")
                .orderId(500L)
                .memberId(7L)
                .idempotencyKey("key-1")
                .pgProvider("TOSS")
                .paymentMethod("CARD")
                .amount(20_000)
                .build();
        payment.recordPreparation("TOSS-1");
        when(paymentRepository.findByPgOrderId("TOSS-1")).thenReturn(Optional.of(payment));
        when(orderRepository.findByIdForUpdate(500L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByPgOrderIdForUpdate("TOSS-1")).thenReturn(Optional.of(payment));

        PaymentConfirmResponse response = service.cancel(7L, null, "TOSS-1");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(payment.getCancelledAt()).isNotNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.getCancelledAt()).isNull();
        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(response.orderStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    void 만료주문의_결제준비는_조회하거나_취소할_수_없다() {
        Order order = order();
        order.changeStatus(OrderStatus.EXPIRED);
        Payment payment = payment();
        when(paymentRepository.findByPgOrderId("TOSS-1")).thenReturn(Optional.of(payment));
        when(orderRepository.findByIdForUpdate(500L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByPgOrderIdForUpdate("TOSS-1")).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.cancel(7L, null, "TOSS-1"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("주문을 찾을 수 없습니다.");
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

    private Payment payment() {
        Payment payment = Payment.builder()
                .paymentNumber("PAY-1")
                .orderId(500L)
                .memberId(7L)
                .idempotencyKey("key-1")
                .pgProvider("TOSS")
                .paymentMethod("CARD")
                .amount(20_000)
                .build();
        payment.recordPreparation("TOSS-1");
        return payment;
    }
}
