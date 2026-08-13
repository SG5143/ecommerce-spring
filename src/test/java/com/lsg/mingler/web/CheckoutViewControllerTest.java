package com.lsg.mingler.web;

import com.lsg.mingler.domain.payment.service.PaymentGatewayResolver;
import com.lsg.mingler.domain.product.service.CategoryService;
import com.lsg.mingler.global.config.SecurityConfig;
import com.lsg.mingler.global.jwt.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(controllers = CheckoutViewController.class)
@Import(SecurityConfig.class)
class CheckoutViewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private CategoryService categoryService;

    @MockitoBean
    private PaymentGatewayResolver paymentGatewayResolver;

    @Test
    void 비회원도_주문서_작성_화면에_접근할_수_있다() throws Exception {
        mockMvc.perform(get("/checkout"))
                .andExpect(status().isOk())
                .andExpect(view().name("checkout/order"));
    }

    @Test
    void 비회원도_결제_확인_화면에_접근할_수_있다() throws Exception {
        when(paymentGatewayResolver.isAvailable("TOSS")).thenReturn(true);

        mockMvc.perform(get("/checkout/payment"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("tossPaymentAvailable", true))
                .andExpect(content().string(containsString("토스페이먼츠 테스트 결제")))
                .andExpect(content().string(containsString("가상 즉시결제")))
                .andExpect(view().name("checkout/payment"));
    }

    @Test
    void 토스_테스트키를_사용할수없어도_가상결제용_결제화면에_접근할_수_있다() throws Exception {
        when(paymentGatewayResolver.isAvailable("TOSS")).thenReturn(false);

        mockMvc.perform(get("/checkout/payment"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("tossPaymentAvailable", false))
                .andExpect(content().string(not(containsString("토스페이먼츠 테스트 결제"))))
                .andExpect(content().string(containsString("가상 즉시결제")))
                .andExpect(view().name("checkout/payment"));
    }

    @Test
    void 비회원도_결제_완료_화면에_접근할_수_있다() throws Exception {
        mockMvc.perform(get("/checkout/complete"))
                .andExpect(status().isOk())
                .andExpect(view().name("checkout/complete"));
    }

    @Test
    void 비회원도_토스_성공과_실패_리다이렉트_화면에_접근할_수_있다() throws Exception {
        mockMvc.perform(get("/checkout/payment/success"))
                .andExpect(status().isOk())
                .andExpect(view().name("checkout/payment-success"));

        mockMvc.perform(get("/checkout/payment/fail"))
                .andExpect(status().isOk())
                .andExpect(view().name("checkout/payment-fail"));
    }
}
