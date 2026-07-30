package com.lsg.mingler.global.error;

import lombok.Getter;

/**
 * 통신 장애, PG 서버 오류 또는 멱등 요청 처리 중처럼 승인 결과를 확정할 수 없을 때 발생
 * 호출자는 결제를 PROCESSING으로 유지하고 조회 API를 통한 재조정을 수행해야 함
 */
@Getter
public class PaymentGatewayUncertainException extends RuntimeException {

    private final String failureCode;

    public PaymentGatewayUncertainException(String failureCode, String message) {
        super(message);
        this.failureCode = failureCode;
    }

    public PaymentGatewayUncertainException(String failureCode, String message, Throwable cause) {
        super(message, cause);
        this.failureCode = failureCode;
    }
}
