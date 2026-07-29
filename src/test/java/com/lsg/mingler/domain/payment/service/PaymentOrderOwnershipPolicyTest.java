package com.lsg.mingler.domain.payment.service;

import com.lsg.mingler.domain.order.entity.Order;
import com.lsg.mingler.global.error.ResourceNotFoundException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentOrderOwnershipPolicyTest {

    @Test
    void 회원ID가_주문회원과_같으면_소유권검증을_통과한다() {
        Order order = order(7L, null);

        assertThatCode(() -> PaymentOrderOwnershipPolicy.validate(order, 7L, null))
                .doesNotThrowAnyException();
    }

    @Test
    void 비회원_토큰해시가_주문토큰과_같으면_소유권검증을_통과한다() {
        Order order = order(null, "guest-hash");

        assertThatCode(() -> PaymentOrderOwnershipPolicy.validate(order, null, "guest-hash"))
                .doesNotThrowAnyException();
    }

    @Test
    void 주문소유자와_일치하지_않으면_존재하지_않는_주문으로_처리한다() {
        Order order = order(8L, null);

        assertThatThrownBy(() -> PaymentOrderOwnershipPolicy.validate(order, 7L, null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("주문을 찾을 수 없습니다");
    }

    private Order order(Long memberId, String guestTokenHash) {
        return Order.builder()
                .orderNumber("ORD-1")
                .memberId(memberId)
                .guestTokenHash(guestTokenHash)
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
    }
}
