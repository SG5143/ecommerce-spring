package com.lsg.mingler.domain.payment.service;

import java.util.Locale;

public enum VirtualPaymentScenario {
    SUCCESS,
    FAILED,
    DELAYED;

    /**
     * 선택 헤더의 가상 결제 시나리오를 정규화한다.
     * 헤더가 없거나 공백이면 정상 승인 시나리오를 사용한다.
     *
     * @param rawScenario 요청 헤더의 가상 결제 시나리오
     * @return 정규화된 시나리오
     * @throws IllegalArgumentException 지원하지 않는 시나리오인 경우
     */
    public static VirtualPaymentScenario from(String rawScenario) {
        if (rawScenario == null || rawScenario.isBlank()) {
            return SUCCESS;
        }
        try {
            return valueOf(rawScenario.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("가상 결제 시나리오는 SUCCESS, FAILED, DELAYED 중 하나여야 합니다.");
        }
    }
}
