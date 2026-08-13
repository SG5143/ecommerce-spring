package com.lsg.mingler.domain.payment.service;

import com.lsg.mingler.domain.order.dao.OrderRepository;
import com.lsg.mingler.domain.order.entity.Order;
import com.lsg.mingler.domain.order.entity.OrderStatus;
import com.lsg.mingler.domain.payment.dao.PaymentRepository;
import com.lsg.mingler.domain.payment.entity.Payment;
import com.lsg.mingler.global.error.ResourceNotFoundException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentAttemptQueryServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private PaymentAttemptQueryService service;

    @Test
    void 만료주문의_결제시도는_조회할_수_없다() {
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
        order.changeStatus(OrderStatus.EXPIRED);
        Payment payment = Payment.builder()
                .paymentNumber("PAY-1")
                .orderId(1L)
                .memberId(7L)
                .idempotencyKey("key-1")
                .pgProvider("VIRTUAL")
                .paymentMethod("CARD")
                .amount(10_000)
                .build();
        payment.recordPreparation("PG-1");
        when(paymentRepository.findByPgOrderId("PG-1")).thenReturn(Optional.of(payment));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.find(7L, null, "PG-1"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("주문을 찾을 수 없습니다.");
    }
}
