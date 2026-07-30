package com.lsg.mingler.domain.payment.service;

import com.lsg.mingler.domain.cart.dao.CartItemRepository;
import com.lsg.mingler.domain.order.dao.OrderItemRepository;
import com.lsg.mingler.domain.order.dao.OrderRepository;
import com.lsg.mingler.domain.order.entity.Order;
import com.lsg.mingler.domain.order.entity.OrderItem;
import com.lsg.mingler.domain.order.entity.OrderStatus;
import com.lsg.mingler.domain.payment.dao.PaymentRepository;
import com.lsg.mingler.domain.payment.dto.PaymentConfirmResponse;
import com.lsg.mingler.domain.payment.entity.Payment;
import com.lsg.mingler.domain.payment.entity.PaymentStatus;
import com.lsg.mingler.global.error.ResourceNotFoundException;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PG 승인 성공 결과를 검증하고 결제 성공, 주문 결제 완료, 장바구니 정리를 한 트랜잭션으로 확정한다.
 */
@Service
@RequiredArgsConstructor
class PaymentCompletionTransactionService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartItemRepository cartItemRepository;

    /**
     * 잠근 주문과 결제에 PG 성공 결과를 반영한다.
     * 이미 완료된 요청은 현재 상태를 반환하며 응답 무결성이 다르면 PROCESSING을 유지한다.
     *
     * @param pgOrderId 로컬 결제를 식별하는 PG 주문번호
     * @param result PG 승인 성공 결과
     * @return 확정 후 결제와 주문 상태
     */
    @Transactional
    PaymentConfirmResponse complete(String pgOrderId, PaymentGatewayResult result) {
        Payment initial = paymentRepository.findByPgOrderId(pgOrderId).orElseThrow(()
                -> new ResourceNotFoundException("결제 정보를 찾을 수 없습니다."));
        Order order = orderRepository.findByIdForUpdate(initial.getOrderId()).orElseThrow(()
                -> new ResourceNotFoundException("주문을 찾을 수 없습니다."));
        Payment payment = paymentRepository.findByPgOrderIdForUpdate(pgOrderId).orElseThrow(()
                -> new ResourceNotFoundException("결제 정보를 찾을 수 없습니다."));

        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            return PaymentConfirmResponseMapper.from(order, payment);
        }

        if (payment.getStatus() != PaymentStatus.PROCESSING) {
            return PaymentConfirmResponseMapper.from(order, payment);
        }

        if (!isValid(payment, result)) {
            payment.markManualReview("토스 승인 응답의 결제키·주문번호·금액·상태가 로컬 결제 정보와 일치하지 않습니다.");
            return PaymentConfirmResponseMapper.from(order, payment);
        }

        payment.markSuccess(
                result.lastTransactionKey(),
                result.approvedAt() == null
                        ? null
                        : result.approvedAt().atZoneSameInstant(BUSINESS_ZONE).toLocalDateTime());

        if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            order.changeStatus(OrderStatus.PAID);
        }

        deleteOrderedCartItems(order.getId());

        return PaymentConfirmResponseMapper.from(order, payment);
    }

    private boolean isValid(Payment payment, PaymentGatewayResult result) {
        return result != null
                && Objects.equals(payment.getPgPaymentKey(), result.paymentKey())
                && Objects.equals(payment.getPgOrderId(), result.orderId())
                && Objects.equals(payment.getAmount(), result.totalAmount())
                && "DONE".equals(result.status())
                && ("카드".equals(result.method()) || "간편결제".equals(result.method()));
    }

    private void deleteOrderedCartItems(Long orderId) {
        List<Long> sourceIds = orderItemRepository.findAllByOrderIdOrderByIdAsc(orderId).stream()
                .map(OrderItem::getSourceCartItemId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (!sourceIds.isEmpty()) {
            cartItemRepository.deleteAllByIdInBatch(sourceIds);
        }
    }
}
