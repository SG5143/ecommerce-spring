package com.lsg.mingler.domain.payment.service;

import com.lsg.mingler.domain.order.entity.Order;
import com.lsg.mingler.domain.order.entity.OrderStatus;
import com.lsg.mingler.global.error.ResourceNotFoundException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentOrderAvailabilityPolicyTest {

    @Test
    void 만료되지_않은_주문은_결제경로에서_사용할_수_있다() {
        assertThatCode(() -> PaymentOrderAvailabilityPolicy.validate(order(false)))
                .doesNotThrowAnyException();
    }

    @Test
    void 만료주문은_결제경로에서_존재하지_않는_주문으로_처리한다() {
        assertThatThrownBy(() -> PaymentOrderAvailabilityPolicy.validate(order(true)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("주문을 찾을 수 없습니다.");
    }

    private Order order(boolean expired) {
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
        if (expired) {
            order.changeStatus(OrderStatus.EXPIRED);
        }
        return order;
    }
}
