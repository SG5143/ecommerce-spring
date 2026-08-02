package com.lsg.mingler.global.error;

/**
 * 결제 키와 PG 주문번호로 결제 결과를 조회했지만 PG에서 해당 결제를 찾지 못했을 때 발생
 */
public class PaymentGatewayNotFoundException extends RuntimeException {

    public PaymentGatewayNotFoundException(String message) {
        super(message);
    }
}
