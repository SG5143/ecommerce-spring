package com.lsg.mingler.domain.payment.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VirtualPaymentScenarioTest {

    @Test
    void 헤더가_없거나_공백이면_성공_시나리오를_사용한다() {
        assertThat(VirtualPaymentScenario.from(null)).isEqualTo(VirtualPaymentScenario.SUCCESS);
        assertThat(VirtualPaymentScenario.from("  ")).isEqualTo(VirtualPaymentScenario.SUCCESS);
    }

    @Test
    void 시나리오_헤더는_공백과_대소문자를_정규화한다() {
        assertThat(VirtualPaymentScenario.from(" delayed "))
                .isEqualTo(VirtualPaymentScenario.DELAYED);
    }

    @Test
    void 지원하지_않는_시나리오는_거부한다() {
        assertThatThrownBy(() -> VirtualPaymentScenario.from("UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SUCCESS, FAILED, DELAYED");
    }
}
