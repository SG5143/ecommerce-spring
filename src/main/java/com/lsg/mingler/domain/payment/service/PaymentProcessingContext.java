package com.lsg.mingler.domain.payment.service;

import com.lsg.mingler.domain.payment.dto.PaymentConfirmResponse;

/**
 * 승인 준비 트랜잭션의 결과와 트랜잭션 밖에서 실행할 PG 승인 명령을 함께 전달
 *
 * @param response 현재 로컬 결제·주문 상태
 * @param gatewayCommand 외부 PG에 전달할 승인 명령
 * @param provider 결제 준비 시 선택된 제공자 코드
 * @param requiresGatewayCall 외부 PG 승인 호출이 필요한지 여부
 */
record PaymentProcessingContext(
        PaymentConfirmResponse response,
        PaymentGatewayCommand gatewayCommand,
        String provider,
        boolean requiresGatewayCall
) {
}
