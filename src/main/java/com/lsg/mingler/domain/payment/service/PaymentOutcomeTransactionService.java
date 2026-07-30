package com.lsg.mingler.domain.payment.service;

import com.lsg.mingler.domain.order.dao.OrderItemRepository;
import com.lsg.mingler.domain.order.dao.OrderRepository;
import com.lsg.mingler.domain.order.entity.Order;
import com.lsg.mingler.domain.payment.dao.PaymentRepository;
import com.lsg.mingler.domain.payment.dto.PaymentConfirmResponse;
import com.lsg.mingler.domain.payment.entity.Payment;
import com.lsg.mingler.domain.payment.entity.PaymentStatus;
import com.lsg.mingler.global.error.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PG가 확정한 실패·취소 결과와 수동 검토 상태를 잠긴 결제에 반영하는 트랜잭션 서비스다.
 */
@Service
@RequiredArgsConstructor
class PaymentOutcomeTransactionService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentInventoryService inventoryService;

    /**
     * PROCESSING 결제를 실패 또는 취소로 전환하고 예약 재고를 한 번 복구한다.
     *
     * @param pgOrderId 처리할 PG 주문번호
     * @param terminalStatus 확정할 FAILED 또는 CANCELLED 상태
     * @param failureCode 실패 분류 코드
     * @param failureReason 실패 또는 취소 사유
     * @return 처리 후 결제와 주문 상태
     */
    @Transactional
    PaymentConfirmResponse finish(String pgOrderId, PaymentStatus terminalStatus, String failureCode, String failureReason) {
        Payment initial = paymentRepository.findByPgOrderId(pgOrderId).orElseThrow(()
                -> new ResourceNotFoundException("결제 정보를 찾을 수 없습니다."));
        Order order = orderRepository.findByIdForUpdate(initial.getOrderId()).orElseThrow(()
                -> new ResourceNotFoundException("주문을 찾을 수 없습니다."));
        Payment payment = paymentRepository.findByPgOrderIdForUpdate(pgOrderId).orElseThrow(()
                -> new ResourceNotFoundException("결제 정보를 찾을 수 없습니다."));

        if (payment.getStatus() != PaymentStatus.PROCESSING) {
            return PaymentConfirmResponseMapper.from(order, payment);
        }

        inventoryService.restore(orderItemRepository.findAllByOrderIdOrderByIdAsc(order.getId()));
        if (terminalStatus == PaymentStatus.CANCELLED) {
            payment.markCancelled(failureCode, failureReason);
        } else {
            payment.markFailed(failureCode, failureReason);
        }

        return PaymentConfirmResponseMapper.from(order, payment);
    }

    /**
     * PG 응답 무결성 불일치처럼 자동 확정할 수 없는 PROCESSING 결제를 수동 검토 대상으로 표시한다.
     *
     * @param pgOrderId 처리할 PG 주문번호
     * @param reason 수동 검토가 필요한 사유
     * @return 표시 후 결제와 주문 상태
     */
    @Transactional
    PaymentConfirmResponse markManualReview(String pgOrderId, String reason) {
        Payment initial = paymentRepository.findByPgOrderId(pgOrderId).orElseThrow(()
                -> new ResourceNotFoundException("결제 정보를 찾을 수 없습니다."));
        Order order = orderRepository.findByIdForUpdate(initial.getOrderId()).orElseThrow(()
                -> new ResourceNotFoundException("주문을 찾을 수 없습니다."));
        Payment payment = paymentRepository.findByPgOrderIdForUpdate(pgOrderId).orElseThrow(()
                -> new ResourceNotFoundException("결제 정보를 찾을 수 없습니다."));

        if (payment.getStatus() == PaymentStatus.PROCESSING) {
            payment.markManualReview(reason);
        }

        return PaymentConfirmResponseMapper.from(order, payment);
    }
}
