package com.lsg.mingler.domain.order.entity;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderStatusTest {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
            OrderStatus.PENDING_PAYMENT, Set.of(OrderStatus.PAID, OrderStatus.CANCELLED),
            OrderStatus.PAID, Set.of(OrderStatus.PREPARING, OrderStatus.CANCELLED),
            OrderStatus.PREPARING, Set.of(OrderStatus.SHIPPING, OrderStatus.CANCELLED),
            OrderStatus.SHIPPING, Set.of(OrderStatus.DELIVERED),
            OrderStatus.DELIVERED, Set.of(OrderStatus.RETURN_REQUESTED),
            OrderStatus.RETURN_REQUESTED, Set.of(OrderStatus.RETURNED),
            OrderStatus.CANCELLED, Set.of(),
            OrderStatus.RETURNED, Set.of()
    );

    @Test
    void 정의된_주문_상태_전이만_허용한다() {
        for (OrderStatus currentStatus : OrderStatus.values()) {
            for (OrderStatus nextStatus : OrderStatus.values()) {
                boolean expected = ALLOWED_TRANSITIONS.get(currentStatus).contains(nextStatus);

                assertThat(currentStatus.canTransitionTo(nextStatus))
                        .as("%s -> %s", currentStatus, nextStatus)
                        .isEqualTo(expected);
            }
        }
    }

    @Test
    void 배송중_이후에는_주문을_취소할_수_없다() {
        assertThat(OrderStatus.SHIPPING.canTransitionTo(OrderStatus.CANCELLED)).isFalse();
        assertThat(OrderStatus.DELIVERED.canTransitionTo(OrderStatus.CANCELLED)).isFalse();
        assertThat(OrderStatus.RETURN_REQUESTED.canTransitionTo(OrderStatus.CANCELLED)).isFalse();
        assertThat(OrderStatus.RETURNED.canTransitionTo(OrderStatus.CANCELLED)).isFalse();
    }

    @Test
    void 동일_상태와_null_상태로의_전이를_거부한다() {
        for (OrderStatus status : OrderStatus.values()) {
            assertThat(status.canTransitionTo(status)).isFalse();
            assertThat(status.canTransitionTo(null)).isFalse();
        }
    }

    @Test
    void 허용되지_않은_전이는_예외를_발생시킨다() {
        assertThatThrownBy(() -> OrderStatus.SHIPPING.validateTransition(OrderStatus.CANCELLED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SHIPPING -> CANCELLED");
    }

    @Test
    void 엔티티는_금지된_전이에서_기존_상태를_유지한다() {
        Order order = order();
        order.changeStatus(OrderStatus.PAID);
        order.changeStatus(OrderStatus.PREPARING);
        order.changeStatus(OrderStatus.SHIPPING);

        assertThatThrownBy(() -> order.changeStatus(OrderStatus.CANCELLED))
                .isInstanceOf(IllegalStateException.class);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPING);
        assertThat(order.getShippingStartedAt()).isNotNull();
        assertThat(order.getCancelledAt()).isNull();
    }

    private Order order() {
        return Order.builder()
                .orderNumber("ORDER-1")
                .memberId(1L)
                .ordererName("주문자")
                .ordererPhone("01012345678")
                .receiverName("수령인")
                .receiverPhone("01012345678")
                .zipcode("12345")
                .address("서울시")
                .merchandiseAmount(10000)
                .totalAmount(10000)
                .build();
    }
}
