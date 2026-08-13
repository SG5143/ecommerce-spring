package com.lsg.mingler.domain.order.api;

import com.lsg.mingler.domain.cart.service.GuestCartTokenManager;
import com.lsg.mingler.domain.order.service.OrderHistoryService;
import com.lsg.mingler.domain.order.service.OrderService;
import com.lsg.mingler.domain.product.service.CategoryService;
import com.lsg.mingler.global.config.SecurityConfig;
import com.lsg.mingler.global.jwt.JwtTokenProvider;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OrderApiController.class)
@Import(SecurityConfig.class)
class OrderApiSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private OrderHistoryService orderHistoryService;

    @MockitoBean
    private GuestCartTokenManager guestCartTokenManager;

    @MockitoBean
    private CategoryService categoryService;

    @Test
    void 미인증_주문내역_조회는_401을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 유효한_회원토큰으로_주문내역을_조회할_수_있다() throws Exception {
        when(jwtTokenProvider.validate("access-token")).thenReturn(true);
        when(jwtTokenProvider.getMemberId("access-token")).thenReturn(7L);
        when(jwtTokenProvider.getRole("access-token")).thenReturn("USER");
        when(orderHistoryService.getHistory(7L, 0))
                .thenReturn(new com.lsg.mingler.domain.order.dto.OrderHistoryResponse(
                        List.of(), 0, 10, 0, 0, false, false));

        mockMvc.perform(get("/api/v1/orders")
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isOk());
    }

    @Test
    void 비회원_주문생성_경로는_인증없이_접근할_수_있다() throws Exception {
        mockMvc.perform(post("/api/v1/orders"))
                .andExpect(status().isBadRequest());
    }
}
