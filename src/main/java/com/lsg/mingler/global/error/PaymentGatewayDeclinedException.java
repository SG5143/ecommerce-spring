package com.lsg.mingler.global.error;

/**
 * PG가 카드 거절이나 승인 만료처럼 결제 실패를 확정했을 때 발생하는 예외
 */
public class PaymentGatewayDeclinedException extends PaymentApprovalException {

    public PaymentGatewayDeclinedException(String failureCode, String message) {
        super(failureCode, message);
    }
}
