package com.lsg.mingler.domain.order.service;

import com.lsg.mingler.domain.order.dao.OrderItemRepository;
import com.lsg.mingler.domain.order.dao.OrderRepository;
import com.lsg.mingler.domain.order.dto.OrderHistoryDisplayStatus;
import com.lsg.mingler.domain.order.dto.OrderHistoryResponse;
import com.lsg.mingler.domain.order.entity.Order;
import com.lsg.mingler.domain.order.entity.OrderItem;
import com.lsg.mingler.domain.order.entity.OrderStatus;
import com.lsg.mingler.domain.payment.dao.PaymentRepository;
import com.lsg.mingler.domain.payment.entity.Payment;
import com.lsg.mingler.domain.payment.entity.PaymentStatus;
import com.lsg.mingler.global.error.AuthenticationException;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderHistoryServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private OrderHistoryService orderHistoryService;

    @Test
    void 회원의_주문을_최신순으로_상품과_대표결제까지_반환한다() {
        Order recentOrder = order(20L, "ORD-20", LocalDateTime.of(2026, 7, 31, 10, 0));
        recentOrder.changeStatus(com.lsg.mingler.domain.order.entity.OrderStatus.PAID);
        Order oldOrder = order(10L, "ORD-10", LocalDateTime.of(2026, 7, 30, 10, 0));
        List<Order> orders = List.of(recentOrder, oldOrder);
        when(orderRepository.findByMemberIdAndStatusNotOrderByCreatedAtDescIdDesc(
                7L,
                OrderStatus.EXPIRED,
                PageRequest.of(0, OrderHistoryService.PAGE_SIZE)
        )).thenReturn(new PageImpl<>(orders, PageRequest.of(0, OrderHistoryService.PAGE_SIZE), 12));

        OrderItem item = orderItem(100L, 20L);
        when(orderItemRepository.findAllByOrderIdInOrderByOrderIdAscIdAsc(List.of(20L, 10L)))
                .thenReturn(List.of(item));

        Payment failed = payment(202L, 20L, "PAY-FAILED", PaymentStatus.FAILED,
                LocalDateTime.of(2026, 7, 31, 10, 5));
        Payment success = payment(201L, 20L, "PAY-SUCCESS", PaymentStatus.SUCCESS,
                LocalDateTime.of(2026, 7, 31, 10, 3));
        when(paymentRepository.findAllByOrderIdInOrderByCreatedAtDescIdDesc(List.of(20L, 10L)))
                .thenReturn(List.of(failed, success));

        OrderHistoryResponse response = orderHistoryService.getHistory(7L, 0);

        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.totalElements()).isEqualTo(12);
        assertThat(response.totalPages()).isEqualTo(2);
        assertThat(response.hasPrevious()).isFalse();
        assertThat(response.hasNext()).isTrue();
        assertThat(response.orders()).extracting(OrderHistoryResponse.OrderSummary::orderNumber)
                .containsExactly("ORD-20", "ORD-10");
        assertThat(response.orders().getFirst().items()).singleElement().satisfies(responseItem -> {
            assertThat(responseItem.orderItemId()).isEqualTo(100L);
            assertThat(responseItem.productName()).isEqualTo("테스트 상품");
            assertThat(responseItem.quantity()).isEqualTo(2);
        });
        assertThat(response.orders().getFirst().payment().paymentNumber()).isEqualTo("PAY-SUCCESS");
        assertThat(response.orders().getFirst().payment().paymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(response.orders().getFirst().displayStatus())
                .isEqualTo(OrderHistoryDisplayStatus.PAYMENT_COMPLETED);
        assertThat(response.orders().getFirst().statusChangedAt())
                .isEqualTo(LocalDateTime.of(2026, 7, 31, 10, 3));
        assertThat(response.orders().get(1).payment()).isNull();
        verify(orderRepository).findByMemberIdAndStatusNotOrderByCreatedAtDescIdDesc(
                7L,
                OrderStatus.EXPIRED,
                PageRequest.of(0, OrderHistoryService.PAGE_SIZE)
        );
    }

    @Test
    void 성공이나_환불계열_결제가_없으면_가장_최근_결제시도를_반환한다() {
        Order order = order(20L, "ORD-20", LocalDateTime.of(2026, 7, 31, 10, 0));
        when(orderRepository.findByMemberIdAndStatusNotOrderByCreatedAtDescIdDesc(
                7L,
                OrderStatus.EXPIRED,
                PageRequest.of(0, OrderHistoryService.PAGE_SIZE)
        )).thenReturn(new PageImpl<>(List.of(order), PageRequest.of(0, OrderHistoryService.PAGE_SIZE), 1));
        when(orderItemRepository.findAllByOrderIdInOrderByOrderIdAscIdAsc(List.of(20L)))
                .thenReturn(List.of());
        Payment recentFailed = payment(202L, 20L, "PAY-FAILED", PaymentStatus.FAILED,
                LocalDateTime.of(2026, 7, 31, 10, 5));
        Payment oldCancelled = payment(201L, 20L, "PAY-CANCELLED", PaymentStatus.CANCELLED,
                LocalDateTime.of(2026, 7, 31, 10, 3));
        when(paymentRepository.findAllByOrderIdInOrderByCreatedAtDescIdDesc(List.of(20L)))
                .thenReturn(List.of(recentFailed, oldCancelled));

        OrderHistoryResponse response = orderHistoryService.getHistory(7L, 0);

        assertThat(response.orders().getFirst().payment().paymentNumber()).isEqualTo("PAY-FAILED");
        assertThat(response.orders().getFirst().payment().paymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(response.orders().getFirst().displayStatus())
                .isEqualTo(OrderHistoryDisplayStatus.PAYMENT_FAILED);
        assertThat(response.orders().getFirst().statusChangedAt())
                .isEqualTo(LocalDateTime.of(2026, 7, 31, 10, 5));
    }

    @Test
    void 결제대기_주문은_대표결제의_상태와_발생시각을_표시한다() {
        List<Order> orders = List.of(
                order(10L, "ORD-NONE", LocalDateTime.of(2026, 7, 31, 10, 0)),
                order(11L, "ORD-PENDING", LocalDateTime.of(2026, 7, 31, 9, 0)),
                order(12L, "ORD-PROCESSING", LocalDateTime.of(2026, 7, 31, 8, 0)),
                order(13L, "ORD-FAILED", LocalDateTime.of(2026, 7, 31, 7, 0)),
                order(14L, "ORD-CANCELLED", LocalDateTime.of(2026, 7, 31, 6, 0))
        );
        when(orderRepository.findByMemberIdAndStatusNotOrderByCreatedAtDescIdDesc(
                7L, OrderStatus.EXPIRED, PageRequest.of(0, OrderHistoryService.PAGE_SIZE)))
                .thenReturn(new PageImpl<>(orders, PageRequest.of(0, OrderHistoryService.PAGE_SIZE), 5));
        when(orderItemRepository.findAllByOrderIdInOrderByOrderIdAscIdAsc(List.of(10L, 11L, 12L, 13L, 14L)))
                .thenReturn(List.of());

        LocalDateTime pendingAt = LocalDateTime.of(2026, 7, 31, 9, 5);
        LocalDateTime processingAt = LocalDateTime.of(2026, 7, 31, 8, 5);
        LocalDateTime failedAt = LocalDateTime.of(2026, 7, 31, 7, 5);
        LocalDateTime cancelledAt = LocalDateTime.of(2026, 7, 31, 6, 5);
        when(paymentRepository.findAllByOrderIdInOrderByCreatedAtDescIdDesc(
                List.of(10L, 11L, 12L, 13L, 14L))).thenReturn(List.of(
                payment(201L, 11L, "PAY-PENDING", PaymentStatus.PENDING, pendingAt),
                payment(202L, 12L, "PAY-PROCESSING", PaymentStatus.PROCESSING, processingAt),
                payment(203L, 13L, "PAY-FAILED", PaymentStatus.FAILED, failedAt),
                payment(204L, 14L, "PAY-CANCELLED", PaymentStatus.CANCELLED, cancelledAt)
        ));

        OrderHistoryResponse response = orderHistoryService.getHistory(7L, 0);

        assertThat(response.orders()).extracting(OrderHistoryResponse.OrderSummary::displayStatus)
                .containsExactly(
                        OrderHistoryDisplayStatus.PAYMENT_PENDING,
                        OrderHistoryDisplayStatus.PAYMENT_PENDING,
                        OrderHistoryDisplayStatus.PAYMENT_PROCESSING,
                        OrderHistoryDisplayStatus.PAYMENT_FAILED,
                        OrderHistoryDisplayStatus.PAYMENT_CANCELLED
                );
        assertThat(response.orders()).extracting(OrderHistoryResponse.OrderSummary::statusChangedAt)
                .containsExactly(null, pendingAt, processingAt, failedAt, cancelledAt);
        assertThat(response.orders().get(4).payment().failureReason()).isEqualTo("결제 처리 사유");
    }

    @Test
    void 환불계열_결제는_결제완료_주문의_표시상태보다_우선한다() {
        Order order = order(20L, "ORD-20", LocalDateTime.of(2026, 7, 31, 10, 0));
        order.changeStatus(OrderStatus.PAID);
        LocalDateTime refundedAt = LocalDateTime.of(2026, 7, 31, 12, 0);
        Payment refunded = payment(201L, 20L, "PAY-REFUNDED", PaymentStatus.REFUNDED, refundedAt);
        mockSingleOrder(order, refunded);

        OrderHistoryResponse response = orderHistoryService.getHistory(7L, 0);

        assertThat(response.orders().getFirst().displayStatus()).isEqualTo(OrderHistoryDisplayStatus.REFUNDED);
        assertThat(response.orders().getFirst().statusChangedAt()).isEqualTo(refundedAt);
    }

    @Test
    void 배송_주문취소_반품상태는_주문상태의_발생시각을_사용한다() {
        LocalDateTime shippingAt = LocalDateTime.of(2026, 7, 31, 11, 0);
        LocalDateTime cancelledAt = LocalDateTime.of(2026, 7, 31, 12, 0);
        LocalDateTime returnedAt = LocalDateTime.of(2026, 7, 31, 13, 0);
        Order shipping = orderWithStatus(20L, "ORD-SHIPPING", OrderStatus.SHIPPING,
                "shippingStartedAt", shippingAt);
        Order cancelled = orderWithStatus(21L, "ORD-CANCELLED", OrderStatus.CANCELLED,
                "cancelledAt", cancelledAt);
        Order returned = orderWithStatus(22L, "ORD-RETURNED", OrderStatus.RETURNED,
                "returnedAt", returnedAt);
        List<Order> orders = List.of(shipping, cancelled, returned);
        when(orderRepository.findByMemberIdAndStatusNotOrderByCreatedAtDescIdDesc(
                7L, OrderStatus.EXPIRED, PageRequest.of(0, OrderHistoryService.PAGE_SIZE)))
                .thenReturn(new PageImpl<>(orders, PageRequest.of(0, OrderHistoryService.PAGE_SIZE), 3));
        when(orderItemRepository.findAllByOrderIdInOrderByOrderIdAscIdAsc(List.of(20L, 21L, 22L)))
                .thenReturn(List.of());
        when(paymentRepository.findAllByOrderIdInOrderByCreatedAtDescIdDesc(List.of(20L, 21L, 22L)))
                .thenReturn(List.of());

        OrderHistoryResponse response = orderHistoryService.getHistory(7L, 0);

        assertThat(response.orders()).extracting(OrderHistoryResponse.OrderSummary::displayStatus)
                .containsExactly(
                        OrderHistoryDisplayStatus.SHIPPING,
                        OrderHistoryDisplayStatus.ORDER_CANCELLED,
                        OrderHistoryDisplayStatus.RETURNED
                );
        assertThat(response.orders()).extracting(OrderHistoryResponse.OrderSummary::statusChangedAt)
                .containsExactly(shippingAt, cancelledAt, returnedAt);
    }

    @Test
    void 범위를_벗어난_페이지는_빈_목록과_페이지정보를_반환한다() {
        PageRequest pageable = PageRequest.of(2, OrderHistoryService.PAGE_SIZE);
        when(orderRepository.findByMemberIdAndStatusNotOrderByCreatedAtDescIdDesc(
                7L, OrderStatus.EXPIRED, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 12));

        OrderHistoryResponse response = orderHistoryService.getHistory(7L, 2);

        assertThat(response.orders()).isEmpty();
        assertThat(response.page()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(12);
        assertThat(response.totalPages()).isEqualTo(2);
        assertThat(response.hasPrevious()).isTrue();
        assertThat(response.hasNext()).isFalse();
        verify(orderItemRepository, never()).findAllByOrderIdInOrderByOrderIdAscIdAsc(org.mockito.ArgumentMatchers.any());
        verify(paymentRepository, never()).findAllByOrderIdInOrderByCreatedAtDescIdDesc(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 음수_페이지는_거부한다() {
        assertThatThrownBy(() -> orderHistoryService.getHistory(7L, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("페이지 번호는 0 이상이어야 합니다.");

        verify(orderRepository, never()).findByMemberIdAndStatusNotOrderByCreatedAtDescIdDesc(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void 회원인증이_없으면_주문내역을_조회할_수_없다() {
        assertThatThrownBy(() -> orderHistoryService.getHistory(null, 0))
                .isInstanceOf(AuthenticationException.class)
                .hasMessage("인증이 필요합니다.");
    }

    private Order order(Long id, String orderNumber, LocalDateTime createdAt) {
        Order order = Order.builder()
                .orderNumber(orderNumber)
                .memberId(7L)
                .ordererName("홍길동")
                .ordererPhone("01012345678")
                .receiverName("홍길동")
                .receiverPhone("01012345678")
                .zipcode("12345")
                .address("서울시")
                .merchandiseAmount(20_000)
                .discountAmount(1_000)
                .shippingFee(3_000)
                .totalAmount(22_000)
                .build();
        ReflectionTestUtils.setField(order, "id", id);
        ReflectionTestUtils.setField(order, "createdAt", createdAt);
        return order;
    }

    private OrderItem orderItem(Long id, Long orderId) {
        OrderItem item = OrderItem.builder()
                .orderId(orderId)
                .productId(30L)
                .productOptionId(40L)
                .productName("테스트 상품")
                .categoryId(50L)
                .categoryName("카테고리")
                .optionName("검정")
                .thumbnailUrl("/images/product.jpg")
                .unitPrice(10_000)
                .quantity(2)
                .lineAmount(20_000)
                .build();
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }

    private Payment payment(
            Long id,
            Long orderId,
            String paymentNumber,
            PaymentStatus status,
            LocalDateTime createdAt
    ) {
        Payment payment = Payment.builder()
                .paymentNumber(paymentNumber)
                .orderId(orderId)
                .memberId(7L)
                .idempotencyKey("key-" + id)
                .pgProvider("VIRTUAL")
                .paymentMethod("CARD")
                .amount(22_000)
                .build();
        ReflectionTestUtils.setField(payment, "id", id);
        ReflectionTestUtils.setField(payment, "status", status);
        ReflectionTestUtils.setField(payment, "createdAt", createdAt);
        ReflectionTestUtils.setField(payment, "failureReason", "결제 처리 사유");
        switch (status) {
            case PROCESSING -> ReflectionTestUtils.setField(payment, "processingAt", createdAt);
            case SUCCESS -> ReflectionTestUtils.setField(payment, "approvedAt", createdAt);
            case FAILED -> ReflectionTestUtils.setField(payment, "failedAt", createdAt);
            case CANCELLED -> ReflectionTestUtils.setField(payment, "cancelledAt", createdAt);
            case REFUND_PENDING -> ReflectionTestUtils.setField(payment, "refundRequestedAt", createdAt);
            case REFUNDED -> ReflectionTestUtils.setField(payment, "refundedAt", createdAt);
            case REFUND_FAILED -> ReflectionTestUtils.setField(payment, "refundFailedAt", createdAt);
            case PENDING -> {
            }
        }
        return payment;
    }

    private void mockSingleOrder(Order order, Payment payment) {
        PageRequest pageable = PageRequest.of(0, OrderHistoryService.PAGE_SIZE);
        when(orderRepository.findByMemberIdAndStatusNotOrderByCreatedAtDescIdDesc(
                7L, OrderStatus.EXPIRED, pageable))
                .thenReturn(new PageImpl<>(List.of(order), pageable, 1));
        when(orderItemRepository.findAllByOrderIdInOrderByOrderIdAscIdAsc(List.of(order.getId())))
                .thenReturn(List.of());
        when(paymentRepository.findAllByOrderIdInOrderByCreatedAtDescIdDesc(List.of(order.getId())))
                .thenReturn(List.of(payment));
    }

    private Order orderWithStatus(
            Long id, String orderNumber, OrderStatus status, String timestampField, LocalDateTime timestamp) {
        Order order = order(id, orderNumber, LocalDateTime.of(2026, 7, 31, 10, 0));
        ReflectionTestUtils.setField(order, "status", status);
        ReflectionTestUtils.setField(order, timestampField, timestamp);
        return order;
    }
}
