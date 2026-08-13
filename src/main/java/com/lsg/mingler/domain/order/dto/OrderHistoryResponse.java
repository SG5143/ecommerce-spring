package com.lsg.mingler.domain.order.dto;

import com.lsg.mingler.domain.order.entity.OrderStatus;
import com.lsg.mingler.domain.payment.entity.PaymentStatus;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 로그인 회원의 주문내역 페이지 응답.
 */
public record OrderHistoryResponse(
        List<OrderSummary> orders,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasPrevious,
        boolean hasNext
) {

    public record OrderSummary(
            String orderNumber,
            LocalDateTime orderedAt,
            OrderStatus orderStatus,
            OrderHistoryDisplayStatus displayStatus,
            LocalDateTime statusChangedAt,
            Integer merchandiseAmount,
            Integer discountAmount,
            Integer shippingFee,
            Integer totalAmount,
            List<Item> items,
            PaymentSummary payment
    ) {
    }

    public record Item(
            Long orderItemId,
            Long productId,
            String productName,
            String optionName,
            String thumbnailUrl,
            Integer unitPrice,
            Integer quantity,
            Integer lineAmount
    ) {
    }

    public record PaymentSummary(
            String paymentNumber,
            PaymentStatus paymentStatus,
            String paymentMethod,
            String pgProvider,
            LocalDateTime approvedAt,
            Integer amount,
            LocalDateTime statusChangedAt,
            String failureReason
    ) {
    }
}
