package com.lsg.mingler.global.error;

public class PaymentDeclinedException extends PaymentApprovalException {

    public static final String FAILURE_CODE = "VIRTUAL_DECLINED";

    public PaymentDeclinedException() {
        super(FAILURE_CODE, "가상 결제 승인이 거절되었습니다. 결제 정보를 확인한 후 다시 시도해주세요.");
    }
}
