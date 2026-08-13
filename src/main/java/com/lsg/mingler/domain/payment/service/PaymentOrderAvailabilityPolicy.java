package com.lsg.mingler.domain.payment.service;

import com.lsg.mingler.domain.order.entity.Order;
import com.lsg.mingler.domain.order.entity.OrderStatus;
import com.lsg.mingler.global.error.ResourceNotFoundException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** 사용자 결제 경로에서 만료 주문을 존재하지 않는 주문으로 처리 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class PaymentOrderAvailabilityPolicy {

    static void validate(Order order) {
        if (order.getStatus() == OrderStatus.EXPIRED) {
            throw new ResourceNotFoundException("주문을 찾을 수 없습니다.");
        }
    }

}
