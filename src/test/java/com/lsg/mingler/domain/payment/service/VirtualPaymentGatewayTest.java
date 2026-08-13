package com.lsg.mingler.domain.payment.service;

import com.lsg.mingler.global.error.PaymentApprovalTimeoutException;
import com.lsg.mingler.global.error.PaymentDeclinedException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VirtualPaymentGatewayTest {

    private final VirtualPaymentGateway gateway = new VirtualPaymentGateway();

    @Test
    void 성공_시나리오는_예외없이_승인된다() {
        assertThatCode(() -> gateway.approve(VirtualPaymentScenario.SUCCESS))
                .doesNotThrowAnyException();
    }

    @Test
    void 실패_시나리오는_승인거절_예외를_발생시킨다() {
        assertThatThrownBy(() -> gateway.approve(VirtualPaymentScenario.FAILED))
                .isInstanceOf(PaymentDeclinedException.class)
                .hasMessageContaining("거절");
    }

    @Test
    void 지연_시나리오는_승인타임아웃_예외를_즉시_발생시킨다() {
        assertThatThrownBy(() -> gateway.approve(VirtualPaymentScenario.DELAYED))
                .isInstanceOf(PaymentApprovalTimeoutException.class)
                .hasMessageContaining("지연");
    }
}
