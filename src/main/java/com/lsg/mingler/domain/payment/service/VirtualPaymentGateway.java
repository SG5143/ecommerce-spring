package com.lsg.mingler.domain.payment.service;

import com.lsg.mingler.global.error.PaymentApprovalTimeoutException;
import com.lsg.mingler.global.error.PaymentDeclinedException;
import org.springframework.stereotype.Component;

@Component
public class VirtualPaymentGateway {

    /**
     * 요청한 가상 시나리오에 따라 승인 성공, 승인 거절 또는 타임아웃을 재현한다.
     * 지연 시나리오는 요청 스레드를 점유하지 않고 즉시 타임아웃으로 처리한다.
     *
     * @param scenario 실행할 가상 결제 시나리오
     */
    public void approve(VirtualPaymentScenario scenario) {
        switch (scenario) {
            case SUCCESS -> {}
            case FAILED -> throw new PaymentDeclinedException();
            case DELAYED -> throw new PaymentApprovalTimeoutException();
        }
    }
}
