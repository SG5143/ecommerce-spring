package com.lsg.mingler.domain.order.service;

import com.lsg.mingler.domain.order.dao.OrderItemRepository;
import com.lsg.mingler.domain.order.dao.OrderRepository;
import com.lsg.mingler.domain.order.dto.OrderHistoryResponse;
import com.lsg.mingler.domain.order.entity.Order;
import com.lsg.mingler.domain.order.entity.OrderItem;
import com.lsg.mingler.domain.payment.dao.PaymentRepository;
import com.lsg.mingler.domain.payment.entity.Payment;
import com.lsg.mingler.domain.payment.entity.PaymentStatus;
import com.lsg.mingler.global.error.AuthenticationException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderHistoryService {

    public static final int PAGE_SIZE = 10;
    private static final Set<PaymentStatus> VALID_PAYMENT_STATUSES = EnumSet.of(
            PaymentStatus.SUCCESS,
            PaymentStatus.REFUND_PENDING,
            PaymentStatus.REFUNDED,
            PaymentStatus.REFUND_FAILED
    );

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;

    /**
     * 로그인 회원의 주문을 최신순으로 조회하고 상품·대표 결제를 일괄 매핑한다.
     */
    @Transactional(readOnly = true)
    public OrderHistoryResponse getHistory(Long memberId, int page) {
        if (memberId == null) {
            throw new AuthenticationException("인증이 필요합니다.");
        }
        if (page < 0) {
            throw new IllegalArgumentException("페이지 번호는 0 이상이어야 합니다.");
        }

        Page<Order> orderPage = orderRepository.findByMemberIdOrderByCreatedAtDescIdDesc(
                memberId,
                PageRequest.of(page, PAGE_SIZE)
        );
        if (orderPage.isEmpty()) {
            return toResponse(orderPage, List.of());
        }

        List<Long> orderIds = orderPage.getContent().stream()
                .map(Order::getId)
                .toList();
        Map<Long, List<OrderItem>> itemsByOrderId = groupItems(
                orderItemRepository.findAllByOrderIdInOrderByOrderIdAscIdAsc(orderIds)
        );
        Map<Long, List<Payment>> paymentsByOrderId = groupPayments(
                paymentRepository.findAllByOrderIdInOrderByCreatedAtDescIdDesc(orderIds)
        );

        List<OrderHistoryResponse.OrderSummary> summaries = orderPage.getContent().stream()
                .map(order -> toSummary(
                        order,
                        itemsByOrderId.getOrDefault(order.getId(), List.of()),
                        selectRepresentativePayment(paymentsByOrderId.getOrDefault(order.getId(), List.of()))
                ))
                .toList();
        return toResponse(orderPage, summaries);
    }

    private Map<Long, List<OrderItem>> groupItems(List<OrderItem> items) {
        Map<Long, List<OrderItem>> grouped = new HashMap<>();
        for (OrderItem item : items) {
            grouped.computeIfAbsent(item.getOrderId(), ignored -> new ArrayList<>()).add(item);
        }
        return grouped;
    }

    private Map<Long, List<Payment>> groupPayments(List<Payment> payments) {
        Map<Long, List<Payment>> grouped = new HashMap<>();
        for (Payment payment : payments) {
            grouped.computeIfAbsent(payment.getOrderId(), ignored -> new ArrayList<>()).add(payment);
        }
        return grouped;
    }

    private Payment selectRepresentativePayment(List<Payment> payments) {
        return payments.stream()
                .filter(payment -> VALID_PAYMENT_STATUSES.contains(payment.getStatus()))
                .findFirst()
                .orElse(payments.isEmpty() ? null : payments.getFirst());
    }

    private OrderHistoryResponse.OrderSummary toSummary(
            Order order,
            List<OrderItem> orderItems,
            Payment payment
    ) {
        List<OrderHistoryResponse.Item> items = orderItems.stream()
                .map(item -> new OrderHistoryResponse.Item(
                        item.getId(),
                        item.getProductId(),
                        item.getProductName(),
                        item.getOptionName(),
                        item.getThumbnailUrl(),
                        item.getUnitPrice(),
                        item.getQuantity(),
                        item.getLineAmount()
                ))
                .toList();

        OrderHistoryResponse.PaymentSummary paymentSummary = payment == null
                ? null
                : new OrderHistoryResponse.PaymentSummary(
                        payment.getPaymentNumber(),
                        payment.getStatus(),
                        payment.getPaymentMethod(),
                        payment.getPgProvider(),
                        payment.getApprovedAt()
                );

        return new OrderHistoryResponse.OrderSummary(
                order.getOrderNumber(),
                order.getCreatedAt(),
                order.getStatus(),
                order.getMerchandiseAmount(),
                order.getDiscountAmount(),
                order.getShippingFee(),
                order.getTotalAmount(),
                items,
                paymentSummary
        );
    }

    private OrderHistoryResponse toResponse(
            Page<Order> orderPage,
            List<OrderHistoryResponse.OrderSummary> summaries
    ) {
        return new OrderHistoryResponse(
                summaries,
                orderPage.getNumber(),
                orderPage.getSize(),
                orderPage.getTotalElements(),
                orderPage.getTotalPages(),
                orderPage.hasPrevious(),
                orderPage.hasNext()
        );
    }
}
