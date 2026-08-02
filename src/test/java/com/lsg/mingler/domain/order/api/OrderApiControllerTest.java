package com.lsg.mingler.domain.order.api;

import com.lsg.mingler.domain.cart.service.GuestCartTokenManager;
import com.lsg.mingler.domain.order.dto.OrderCreateRequest;
import com.lsg.mingler.domain.order.dto.OrderCreateResponse;
import com.lsg.mingler.domain.order.dto.OrderHistoryResponse;
import com.lsg.mingler.domain.order.entity.OrderStatus;
import com.lsg.mingler.domain.order.service.OrderHistoryService;
import com.lsg.mingler.domain.order.service.OrderService;
import com.lsg.mingler.global.error.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrderApiControllerTest {

    @Mock
    private OrderService orderService;

    @Mock
    private OrderHistoryService orderHistoryService;

    @Mock
    private GuestCartTokenManager guestCartTokenManager;

    @InjectMocks
    private OrderApiController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 회원ID와_페이지를_서비스에_전달한다() {
        OrderHistoryResponse expected = new OrderHistoryResponse(
                List.of(), 1, 10, 0, 0, false, false);
        when(orderHistoryService.getHistory(7L, 1)).thenReturn(expected);

        org.springframework.http.ResponseEntity<OrderHistoryResponse> response =
                controller.getHistory(7L, 1);

        org.assertj.core.api.Assertions.assertThat(response.getBody()).isSameAs(expected);
        verify(orderHistoryService).getHistory(7L, 1);
    }

    @Test
    void 음수_페이지는_400을_반환한다() throws Exception {
        when(orderHistoryService.getHistory(null, -1))
                .thenThrow(new IllegalArgumentException("페이지 번호는 0 이상이어야 합니다."));

        mockMvc.perform(get("/api/v1/orders").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {"message":"페이지 번호는 0 이상이어야 합니다."}
                        """));
    }

    @Test
    void 비회원_즉시구매는_장바구니쿠키없이_주문을_생성한다() {
        OrderCreateRequest request = new OrderCreateRequest(
                null,
                List.of(new OrderCreateRequest.DirectItem(10L, 20L, 1)),
                new OrderCreateRequest.Orderer("비회원", "010-1234-5678", null),
                new OrderCreateRequest.Receiver("수령인", "010-1234-5678", "12345", "서울", null),
                null);
        OrderCreateResponse expected = new OrderCreateResponse(
                "ORD-1", OrderStatus.PENDING_PAYMENT, 10_000, 0, 0, 10_000,
                List.of(), "guest-order-token");
        when(guestCartTokenManager.hash(null)).thenReturn(null);
        when(orderService.createOrder(null, null, request)).thenReturn(expected);

        org.springframework.http.ResponseEntity<OrderCreateResponse> response =
                controller.createOrder(null, null, request);

        org.assertj.core.api.Assertions.assertThat(response.getStatusCode().value()).isEqualTo(201);
        org.assertj.core.api.Assertions.assertThat(response.getBody()).isSameAs(expected);
        verify(orderService).createOrder(null, null, request);
    }
}
