package com.lsg.mingler.domain.payment.api;

import com.lsg.mingler.domain.payment.dto.PaymentConfirmRequest;
import com.lsg.mingler.domain.payment.service.PaymentService;
import com.lsg.mingler.global.error.GlobalExceptionHandler;
import com.lsg.mingler.global.util.HashUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PaymentApiControllerTest {

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PaymentApiController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

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

    @Test
    void 결제요청_본문이_비어있으면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/payments/confirm")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {"message":"요청 본문이 올바르지 않습니다."}
                        """));
    }

    @Test
    void 결제요청_JSON이_깨져있으면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/payments/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {"message":"요청 본문이 올바르지 않습니다."}
                        """));
    }

    @Test
    void 결제금액_타입이_올바르지_않으면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/payments/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderNumber": "ORD-1",
                                  "amount": "금액",
                                  "paymentMethod": "CARD"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {"message":"요청 본문이 올바르지 않습니다."}
                        """));
    }
}
