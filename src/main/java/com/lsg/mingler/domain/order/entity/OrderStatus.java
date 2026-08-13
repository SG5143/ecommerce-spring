package com.lsg.mingler.domain.order.entity;

import java.util.EnumSet;
import java.util.Set;

public enum OrderStatus {
    PENDING_PAYMENT, // 결제 대기
    PAID,            // 결제 완료
    PREPARING,       // 상품 준비중
    SHIPPING,        // 배송중
    DELIVERED,       // 배송 완료
    CANCELLED,       // 주문 취소
    RETURN_REQUESTED,// 반품 요청
    RETURNED,        // 반품 완료
    EXPIRED;         // 결제 기한 만료

    private static final Set<OrderStatus> NO_TRANSITIONS = Set.of();

    /**
     * 현재 상태에서 다음 상태로 변경 가능한지 확인한다.
     */
    public boolean canTransitionTo(OrderStatus nextStatus) {
        return nextStatus != null && allowedTransitions().contains(nextStatus);
    }

    /**
     * 허용되지 않은 상태 변화일 경우 예외를 발생시킨다.
     */
    public void validateTransition(OrderStatus nextStatus) {
        if (!canTransitionTo(nextStatus)) {
            throw new IllegalStateException("허용되지 않는 주문 상태 전이입니다: " + this + " -> " + nextStatus);
        }
    }

    private Set<OrderStatus> allowedTransitions() {
        return switch (this) {
            case PENDING_PAYMENT -> EnumSet.of(PAID, CANCELLED, EXPIRED);
            case PAID -> EnumSet.of(PREPARING, CANCELLED);
            case PREPARING -> EnumSet.of(SHIPPING, CANCELLED);
            case SHIPPING -> EnumSet.of(DELIVERED);
            case DELIVERED -> EnumSet.of(RETURN_REQUESTED);
            case RETURN_REQUESTED -> EnumSet.of(RETURNED);
            case CANCELLED, RETURNED, EXPIRED -> NO_TRANSITIONS;
        };
    }
}
