package com.lsg.mingler.domain.payment.api;

import com.lsg.mingler.domain.payment.dto.PaymentConfirmRequest;
import com.lsg.mingler.domain.payment.service.PaymentService;
import com.lsg.mingler.global.util.HashUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentApiControllerTest {

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PaymentApiController controller;

    @Test
    void 회원_결제는_가상결제_시나리오_헤더를_서비스에_전달한다() {
        PaymentConfirmRequest request = new PaymentConfirmRequest("ORD-1", 10_000, "CARD");

        controller.confirm(7L, "key-1", null, "FAILED", request);

        verify(paymentService).confirm(7L, null, "key-1", "FAILED", request);
    }

    @Test
    void 비회원_결제는_주문토큰을_해시로_변환해_전달한다() {
        PaymentConfirmRequest request = new PaymentConfirmRequest("ORD-1", 10_000, "CARD");

        controller.confirm(null, "key-1", "guest-token", null, request);

        verify(paymentService).confirm(
                null,
                HashUtils.sha256Hex("guest-token"),
                "key-1",
                null,
                request);
    }
}
