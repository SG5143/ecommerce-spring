package com.lsg.mingler.web;

import com.lsg.mingler.domain.product.service.CategoryService;
import com.lsg.mingler.global.config.SecurityConfig;
import com.lsg.mingler.global.jwt.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @Test
    void 비회원도_주문서_작성_화면에_접근할_수_있다() throws Exception {
        mockMvc.perform(get("/checkout"))
                .andExpect(status().isOk())
                .andExpect(view().name("checkout/order"));
    }

    @Test
    void 비회원도_결제_확인_화면에_접근할_수_있다() throws Exception {
        mockMvc.perform(get("/checkout/payment"))
                .andExpect(status().isOk())
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
