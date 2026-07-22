package com.lsg.mingler.domain.payment.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * 결제 도메인의 상태와 허용 가능한 다음 상태 규칙을 정의
 */
public enum PaymentStatus {
    PENDING,        // 결재 승인 전 상태
    SUCCESS,        // 결제 승인 완료
    FAILED,         // 결제 실패
    CANCELLED,      // 결제 취소 완료
    REFUND_PENDING, // 환불 요청 접수
    REFUNDED,       // 환불 요청 완료
    REFUND_FAILED;  // 환불 실패(재시도 가능)

    private static final Set<PaymentStatus> NO_TRANSITIONS = Set.of();

    /**
     * 현재 상태에서 다음 상태로 변경 가능한지 확인한다.
     */
    public boolean canTransitionTo(PaymentStatus nextStatus) {
        return nextStatus != null && allowedTransitions().contains(nextStatus);
    }

    /**
     * 허용되지 않은 상태 변화일 경우 예외를 발생시킨다.
     */
    public void validateTransition(PaymentStatus nextStatus) {
        if (!canTransitionTo(nextStatus)) {
            throw new IllegalStateException("허용되지 않는 결제 상태 전이입니다: " + this + " -> " + nextStatus);
        }
    }

    private Set<PaymentStatus> allowedTransitions() {
        return switch (this) {
            case PENDING -> EnumSet.of(SUCCESS, FAILED, CANCELLED);
            case SUCCESS -> EnumSet.of(REFUND_PENDING);
            case REFUND_PENDING -> EnumSet.of(REFUNDED, REFUND_FAILED);
            case REFUND_FAILED -> EnumSet.of(REFUND_PENDING);
            case FAILED, CANCELLED, REFUNDED -> NO_TRANSITIONS;
        };
    }
}
