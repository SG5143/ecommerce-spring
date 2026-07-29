package com.lsg.mingler.domain.payment.service;

import com.lsg.mingler.domain.order.entity.Order;
import com.lsg.mingler.global.error.ResourceNotFoundException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class PaymentOrderOwnershipPolicy {

    /**
     * 회원 ID 또는 비회원 주문 토큰 해시가 주문 소유자와 일치하는지 확인한다.
     * 소유하지 않은 주문의 존재 여부가 노출되지 않도록 불일치 시 404 예외를 사용한다.
     *
     * @param order 소유권을 확인할 주문
     * @param memberId 인증된 회원 ID이며 비회원 요청이면 {@code null}
     * @param guestOrderTokenHash 비회원 주문 토큰 해시이며 회원 요청이면 {@code null}
     */
    static void validate(Order order, Long memberId, String guestOrderTokenHash) {
        if (memberId != null) {
            if (!memberId.equals(order.getMemberId())) {
                throw new ResourceNotFoundException("주문을 찾을 수 없습니다.");
            }
            return;
        }
        if (guestOrderTokenHash == null || !guestOrderTokenHash.equals(order.getGuestTokenHash())) {
            throw new ResourceNotFoundException("주문을 찾을 수 없습니다.");
        }
    }
}
