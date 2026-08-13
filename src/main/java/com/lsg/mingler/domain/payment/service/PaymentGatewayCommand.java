package com.lsg.mingler.domain.payment.service;

/**
 * DB 트랜잭션에서 검증을 마친 뒤 외부 PG 승인 호출에 전달하는 불변값
 *
 * @param paymentKey PG 인증 성공 후 발급된 결제 키
 * @param orderId 결제 준비 시 발급한 PG 주문번호
 * @param amount 서버에서 검증한 승인 금액
 * @param idempotencyKey Mingler와 PG가 함께 사용하는 멱등성 키
 * @param virtualScenario 가상 결제 구현에서 재현할 시나리오
 */
public record PaymentGatewayCommand(
        String paymentKey,
        String orderId,
        Integer amount,
        String idempotencyKey,
        VirtualPaymentScenario virtualScenario
) {
}
