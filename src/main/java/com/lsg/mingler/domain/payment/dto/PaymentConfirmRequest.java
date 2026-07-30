package com.lsg.mingler.domain.payment.dto;

public record PaymentConfirmRequest(
        String paymentKey,
        String orderId,
        Integer amount,
        String orderNumber,
        String paymentMethod
) {

    public PaymentConfirmRequest(String paymentKey, String orderId, Integer amount) {
        this(paymentKey, orderId, amount, null, null);
    }

    /**
     * 기존 가상 결제 단위 테스트와 내부 호출의 컴파일 호환을 위한 생성자
     */
    public PaymentConfirmRequest(String orderNumber, Integer amount, String paymentMethod) {
        this(null, null, amount, orderNumber, paymentMethod);
    }
}
