package com.lsg.mingler.domain.order.dto;

/**
 * 주문 상태와 대표 결제 상태를 조합한 마이샵 주문내역 표시 상태.
 */
public enum OrderHistoryDisplayStatus {
    PAYMENT_PENDING,
    PAYMENT_PROCESSING,
    PAYMENT_COMPLETED,
    PAYMENT_FAILED,
    PAYMENT_CANCELLED,
    PREPARING,
    SHIPPING,
    DELIVERED,
    ORDER_CANCELLED,
    RETURN_REQUESTED,
    RETURNED,
    REFUND_PENDING,
    REFUNDED,
    REFUND_FAILED
}
