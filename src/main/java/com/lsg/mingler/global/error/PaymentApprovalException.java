package com.lsg.mingler.global.error;

import lombok.Getter;

@Getter
public abstract class PaymentApprovalException extends RuntimeException {

    private final String failureCode;

    protected PaymentApprovalException(String failureCode, String message) {
        super(message);
        this.failureCode = failureCode;
    }
}
