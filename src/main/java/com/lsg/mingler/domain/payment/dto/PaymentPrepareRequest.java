package com.lsg.mingler.domain.payment.dto;

/**
 * 결제 준비에 필요한 주문번호와 결제수단을 전달하는 요청 DTO
 * 결제 금액은 클라이언트에서 받지 않고 서버의 주문 스냅샷으로 계산
 *
 * @param orderNumber 결제할 주문번호
 * @param paymentMethod 요청할 결제수단
 */
public record PaymentPrepareRequest(
        String orderNumber,
        String paymentMethod
) {
}
