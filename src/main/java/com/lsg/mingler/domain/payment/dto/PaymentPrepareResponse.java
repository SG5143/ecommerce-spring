package com.lsg.mingler.domain.payment.dto;

/**
 * 클라이언트가 PG 결제창을 호출할 때 사용하는 결제 준비 정보
 *
 * @param clientKey 결제 SDK 초기화에 사용할 공개 클라이언트 키
 * @param orderId 결제 시도별로 발급한 PG 주문번호
 * @param orderName 결제창에 표시할 주문명
 * @param amount 서버의 주문 스냅샷으로 계산한 결제 금액
 * @param currency 결제 통화
 * @param customerKey 결제 시도별 비추측성 고객 식별자
 * @param paymentProvider 서버가 확정해 결제 시도에 저장한 결제 제공자
 */
public record PaymentPrepareResponse(
        String clientKey,
        String orderId,
        String orderName,
        Integer amount,
        String currency,
        String customerKey,
        String paymentProvider
) {
}
