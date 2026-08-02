package com.lsg.mingler.domain.payment.service;

import java.time.OffsetDateTime;

/**
 * 결제 제공사의 승인 또는 조회 응답 결과
 *
 * @param paymentKey PG 결제 키
 * @param orderId PG 주문번호
 * @param totalAmount PG가 확인한 총 결제 금액
 * @param status PG 결제 상태
 * @param method PG가 확인한 결제수단
 * @param lastTransactionKey PG의 마지막 거래 키
 * @param approvedAt PG 승인 시각
 * @param failureCode PG 실패 코드
 * @param failureMessage PG 실패 사유
 */
public record PaymentGatewayResult(
        String paymentKey,
        String orderId,
        Integer totalAmount,
        String status,
        String method,
        String lastTransactionKey,
        OffsetDateTime approvedAt,
        String failureCode,
        String failureMessage
) {
}
