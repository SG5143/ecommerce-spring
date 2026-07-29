package com.lsg.mingler.domain.payment.service;

import com.lsg.mingler.domain.order.entity.Order;
import com.lsg.mingler.domain.payment.entity.Payment;
import com.lsg.mingler.global.error.DuplicateException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class PaymentRequestPolicy {

    private static final String PG_PROVIDER = "VIRTUAL";

    /**
     * 저장된 결제가 현재 결제 명령과 같은 요청에서 생성되었는지 검증한다.
     *
     * @param order 현재 결제 대상 주문
     * @param payment 같은 멱등성 키로 저장된 결제
     * @param command 현재 결제 승인 명령
     * @throws DuplicateException 주문·금액·결제수단·PG 제공사가 다른 경우
     */
    static void validateSameRequest(Order order, Payment payment, PaymentService.PaymentConfirmCommand command) {
        boolean sameRequest = order.getId().equals(payment.getOrderId())
                && command.amount().equals(payment.getAmount())
                && command.paymentMethod().equals(payment.getPaymentMethod())
                && PG_PROVIDER.equals(payment.getPgProvider());
        if (!sameRequest) {
            throw new DuplicateException("결제 요청 정보가 이전 요청과 달라 처리할 수 없습니다. 결제 내용을 확인한 후 다시 시도해주세요.");
        }
    }
}
