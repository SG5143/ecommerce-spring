package com.lsg.mingler.domain.payment.api;

import com.lsg.mingler.domain.payment.dto.PaymentConfirmRequest;
import com.lsg.mingler.domain.payment.dto.PaymentConfirmResponse;
import com.lsg.mingler.domain.payment.dto.PaymentPrepareRequest;
import com.lsg.mingler.domain.payment.dto.PaymentPrepareResponse;
import com.lsg.mingler.domain.payment.entity.PaymentStatus;
import com.lsg.mingler.domain.payment.service.PaymentAttemptQueryService;
import com.lsg.mingler.domain.payment.service.PaymentFlowService;
import com.lsg.mingler.domain.payment.service.PaymentPreparationCancelService;
import com.lsg.mingler.domain.payment.service.PaymentPreparationService;
import com.lsg.mingler.global.error.GlobalExceptionHandler;
import com.lsg.mingler.global.util.HashUtils;
import com.lsg.mingler.domain.order.entity.OrderStatus;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PaymentApiControllerTest {

    @Mock
    private PaymentFlowService paymentFlowService;
    @Mock
    private PaymentPreparationService preparationService;
    @Mock
    private PaymentAttemptQueryService queryService;
    @Mock
    private PaymentPreparationCancelService cancelService;

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
        PaymentConfirmRequest request = new PaymentConfirmRequest("payment-key", "VIRTUAL-order", 10_000);
        when(paymentFlowService.confirm(7L, null, "key-1", "FAILED", request))
                .thenReturn(successResponse());

        controller.confirm(7L, "key-1", null, "FAILED", request);

        verify(paymentFlowService).confirm(7L, null, "key-1", "FAILED", request);
    }

    @Test
    void 비회원_결제는_주문토큰을_해시로_변환해_전달한다() {
        PaymentConfirmRequest request = new PaymentConfirmRequest("payment-key", "VIRTUAL-order", 10_000);
        when(paymentFlowService.confirm(
                null,
                HashUtils.sha256Hex("guest-token"),
                "key-1",
                null,
                request)).thenReturn(successResponse());

        controller.confirm(null, "key-1", "guest-token", null, request);

        verify(paymentFlowService).confirm(
                null,
                HashUtils.sha256Hex("guest-token"),
                "key-1",
                null,
                request);
    }

    @Test
    void 비회원_결제준비는_주문토큰을_해시로_변환한다() {
        PaymentPrepareRequest request = new PaymentPrepareRequest("ORD-1", "CARD");
        PaymentPrepareResponse response = new PaymentPrepareResponse(
                null, "VIRTUAL-order", "상품", 10_000, "KRW", "customer-key");
        when(preparationService.prepare(
                null,
                HashUtils.sha256Hex("guest-token"),
                "key-1",
                request)).thenReturn(response);

        controller.prepare(null, "key-1", "guest-token", request);

        verify(preparationService).prepare(
                null,
                HashUtils.sha256Hex("guest-token"),
                "key-1",
                request);
    }

    @Test
    void 승인결과가_PROCESSING이면_202를_반환한다() {
        PaymentConfirmRequest request = new PaymentConfirmRequest(
                "payment-key", "TOSS-order", 10_000);
        when(paymentFlowService.confirm(7L, null, "key-1", null, request))
                .thenReturn(processingResponse());

        org.springframework.http.ResponseEntity<PaymentConfirmResponse> response =
                controller.confirm(7L, "key-1", null, null, request);

        org.assertj.core.api.Assertions.assertThat(response.getStatusCode().value()).isEqualTo(202);
    }

    private PaymentConfirmResponse successResponse() {
        return new PaymentConfirmResponse(
                "PAY-1",
                "ORD-1",
                PaymentStatus.SUCCESS,
                OrderStatus.PAID,
                10_000,
                "CARD",
                "VIRTUAL",
                "VPG-1",
                null);
    }

    private PaymentConfirmResponse processingResponse() {
        return new PaymentConfirmResponse(
                "PAY-1",
                "ORD-1",
                PaymentStatus.PROCESSING,
                OrderStatus.PENDING_PAYMENT,
                10_000,
                "CARD",
                "TOSS",
                null,
                null);
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
