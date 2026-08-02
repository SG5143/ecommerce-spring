package com.lsg.mingler.domain.order.service;

import com.lsg.mingler.domain.order.dao.OrderRepository;
import com.lsg.mingler.domain.order.entity.OrderStatus;
import com.lsg.mingler.domain.payment.entity.PaymentStatus;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderExpirationServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderExpirationTransactionService transactionService;

    private final OrderExpirationProperties properties =
            new OrderExpirationProperties(Duration.ofMinutes(30), Duration.ofMinutes(1), 100);

    @Test
    void 회당_처리량만큼_후보를_조회하고_주문별_트랜잭션으로_만료한다() {
        OrderExpirationService service = new OrderExpirationService(
                orderRepository, transactionService, properties);
        when(orderRepository.findExpirationCandidateIds(
                eq(OrderStatus.PENDING_PAYMENT), any(), eq(PaymentStatus.PENDING), any(), any(),
                eq(PageRequest.of(0, 100))))
                .thenReturn(List.of(1L, 2L));
        when(transactionService.expire(eq(1L), any(), any())).thenReturn(true);
        when(transactionService.expire(eq(2L), any(), any())).thenThrow(new IllegalStateException("실패"));

        service.expirePendingOrders();

        verify(transactionService).expire(eq(1L), any(), any());
        verify(transactionService).expire(eq(2L), any(), any());
    }
}
