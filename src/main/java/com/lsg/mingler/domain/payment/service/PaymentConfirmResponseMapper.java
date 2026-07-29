package com.lsg.mingler.domain.payment.service;

import com.lsg.mingler.domain.order.entity.Order;
import com.lsg.mingler.domain.payment.dto.PaymentConfirmResponse;
import com.lsg.mingler.domain.payment.entity.Payment;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class PaymentConfirmResponseMapper {

    static PaymentConfirmResponse from(Order order, Payment payment) {
        return new PaymentConfirmResponse(
                payment.getPaymentNumber(),
                order.getOrderNumber(),
                payment.getStatus(),
                order.getStatus(),
                payment.getAmount(),
                payment.getPaymentMethod(),
                payment.getPgProvider(),
                payment.getPgTransactionKey(),
                payment.getApprovedAt());
    }
}
