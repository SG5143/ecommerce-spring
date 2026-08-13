package com.lsg.mingler.domain.order.service;

import com.lsg.mingler.domain.order.dao.OrderRepository;
import com.lsg.mingler.domain.order.entity.Order;
import com.lsg.mingler.domain.order.entity.OrderStatus;
import com.lsg.mingler.domain.payment.dao.PaymentRepository;
import com.lsg.mingler.domain.payment.entity.Payment;
import com.lsg.mingler.domain.payment.entity.PaymentStatus;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 주문별 잠금과 재검증을 수행해 결제 대기 주문을 독립 트랜잭션에서 만료한다. */
@Service
@RequiredArgsConstructor
public class OrderExpirationTransactionService {

    private static final EnumSet<PaymentStatus> BLOCKING_STATUSES = EnumSet.of(
            PaymentStatus.PROCESSING,
            PaymentStatus.SUCCESS,
            PaymentStatus.REFUND_PENDING,
            PaymentStatus.REFUNDED,
            PaymentStatus.REFUND_FAILED
    );

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean expire(Long orderId, LocalDateTime orderCutoff, LocalDateTime pendingCutoff) {
        Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (!isExpiredOrderCandidate(order, orderCutoff)) {
            return false;
        }

        List<Payment> payments = paymentRepository.findAllByOrderIdOrderByCreatedAtDescIdDesc(orderId);
        boolean blocked = payments.stream().anyMatch(payment -> isBlocking(payment, pendingCutoff));
        if (blocked) {
            return false;
        }

        payments.stream()
                .filter(payment -> payment.getStatus() == PaymentStatus.PENDING)
                .forEach(payment -> payment.markCancelled(
                        "ORDER_EXPIRED", "주문 결제 기한이 만료되었습니다."));
        order.changeStatus(OrderStatus.EXPIRED);
        return true;
    }

    private boolean isExpiredOrderCandidate(Order order, LocalDateTime orderCutoff) {
        return order != null
                && order.getStatus() == OrderStatus.PENDING_PAYMENT
                && order.getCreatedAt() != null
                && order.getCreatedAt().isBefore(orderCutoff);
    }

    private boolean isBlocking(Payment payment, LocalDateTime pendingCutoff) {
        if (BLOCKING_STATUSES.contains(payment.getStatus())) {
            return true;
        }
        return payment.getStatus() == PaymentStatus.PENDING
                && payment.getCreatedAt() != null
                && !payment.getCreatedAt().isBefore(pendingCutoff);
    }
}
