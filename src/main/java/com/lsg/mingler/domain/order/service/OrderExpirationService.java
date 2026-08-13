package com.lsg.mingler.domain.order.service;

import com.lsg.mingler.domain.order.dao.OrderRepository;
import com.lsg.mingler.domain.order.entity.OrderStatus;
import com.lsg.mingler.domain.payment.entity.PaymentStatus;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** 결제 기한이 지난 주문 후보를 주기적으로 찾아 주문별 만료 트랜잭션을 실행 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderExpirationService {

    private static final EnumSet<PaymentStatus> BLOCKING_STATUSES = EnumSet.of(
            PaymentStatus.PROCESSING,
            PaymentStatus.SUCCESS,
            PaymentStatus.REFUND_PENDING,
            PaymentStatus.REFUNDED,
            PaymentStatus.REFUND_FAILED
    );

    private final OrderRepository orderRepository;
    private final OrderExpirationTransactionService transactionService;
    private final OrderExpirationProperties properties;

    @Scheduled(fixedDelayString = "${order.expiration.fixed-delay:PT1M}")
    public void expirePendingOrders() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minus(properties.ttl());
        List<Long> candidates = orderRepository.findExpirationCandidateIds(
                OrderStatus.PENDING_PAYMENT,
                cutoff,
                PaymentStatus.PENDING,
                cutoff,
                BLOCKING_STATUSES,
                PageRequest.of(0, properties.batchSize())
        );

        int expiredCount = 0;
        for (Long orderId : candidates) {
            try {
                if (transactionService.expire(orderId, cutoff, cutoff)) {
                    expiredCount++;
                }
            } catch (RuntimeException exception) {
                log.error("결제 대기 주문 만료 처리에 실패했습니다. orderId={}", orderId, exception);
            }
        }
        if (expiredCount > 0) {
            log.info("결제 기한이 지난 주문 {}건을 만료 처리했습니다.", expiredCount);
        }
    }
}
