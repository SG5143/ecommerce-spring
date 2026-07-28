package com.lsg.mingler.domain.payment.dto;

public record PaymentConfirmRequest(
        String orderNumber,
        Integer amount,
        String paymentMethod
) {
}
