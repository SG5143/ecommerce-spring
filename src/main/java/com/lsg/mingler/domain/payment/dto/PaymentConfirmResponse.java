package com.lsg.mingler.domain.payment.dto;

import com.lsg.mingler.domain.order.entity.OrderStatus;
import com.lsg.mingler.domain.payment.entity.PaymentStatus;
import java.time.LocalDateTime;

public record PaymentConfirmResponse(
        String paymentNumber,
        String orderNumber,
        PaymentStatus paymentStatus,
        OrderStatus orderStatus,
        Integer amount,
        String paymentMethod,
        String pgProvider,
        String pgOrderId,
        String pgTransactionKey,
        LocalDateTime approvedAt,
        String failureCode,
        String failureReason
) {

    public PaymentConfirmResponse(
            String paymentNumber,
            String orderNumber,
            PaymentStatus paymentStatus,
            OrderStatus orderStatus,
            Integer amount,
            String paymentMethod,
            String pgProvider,
            String pgTransactionKey,
            LocalDateTime approvedAt) {
        this(paymentNumber, orderNumber, paymentStatus, orderStatus, amount, paymentMethod,
                pgProvider, null, pgTransactionKey, approvedAt, null, null);
    }
}
