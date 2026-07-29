package com.lsg.mingler.global.error;

public class PaymentApprovalTimeoutException extends PaymentApprovalException {

    public PaymentApprovalTimeoutException() {
        super("VIRTUAL_APPROVAL_TIMEOUT", "가상 결제 승인이 지연되어 처리 결과를 확인하지 못했습니다. 새 요청으로 다시 시도해주세요.");
    }
}
