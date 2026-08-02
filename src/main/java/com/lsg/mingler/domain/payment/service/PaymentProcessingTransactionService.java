package com.lsg.mingler.domain.payment.service;

import com.lsg.mingler.domain.order.dao.OrderItemRepository;
import com.lsg.mingler.domain.order.dao.OrderRepository;
import com.lsg.mingler.domain.order.entity.Order;
import com.lsg.mingler.domain.order.entity.OrderItem;
import com.lsg.mingler.domain.order.entity.OrderStatus;
import com.lsg.mingler.domain.payment.dao.PaymentRepository;
import com.lsg.mingler.domain.payment.entity.Payment;
import com.lsg.mingler.domain.payment.entity.PaymentStatus;
import com.lsg.mingler.global.error.ConflictException;
import com.lsg.mingler.global.error.DuplicateException;
import com.lsg.mingler.global.error.PaymentGatewayDeclinedException;
import com.lsg.mingler.global.error.ResourceNotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 외부 PG 승인 호출 전에 주문·결제·재고를 잠그고 PROCESSING 상태와 재고 예약을 확정
 */
@Service
@RequiredArgsConstructor
class PaymentProcessingTransactionService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentInventoryService inventoryService;

    /**
     * 승인 요청값과 결제 준비 정보를 재검증하고 재고를 한 번 예약한 뒤 PROCESSING으로 전환한다.
     * 중복 요청은 현재 상태를 반환해 외부 PG와 재고가 중복 처리되지 않도록 한다.
     *
     * @param memberId 인증된 회원 ID
     * @param guestOrderTokenHash 비회원 주문 토큰 해시
     * @param idempotencyKey 결제 준비와 동일한 멱등성 키
     * @param paymentKey PG 인증 성공 후 발급된 결제 키
     * @param pgOrderId 결제 준비 시 발급한 PG 주문번호
     * @param amount 클라이언트 콜백에 포함된 승인 금액
     * @param rawVirtualScenario 가상 결제에서 재현할 시나리오 원문
     * @return 현재 상태와 외부 PG 호출 필요 여부를 포함한 처리 문맥
     */
    @Transactional
    PaymentProcessingContext start(
            Long memberId,
            String guestOrderTokenHash,
            String idempotencyKey,
            String paymentKey,
            String pgOrderId,
            Integer amount,
            String rawVirtualScenario) {
        Payment initial = paymentRepository.findByPgOrderId(pgOrderId).orElseThrow(()
                -> new ResourceNotFoundException("결제 준비 정보를 찾을 수 없습니다."));
        Order order = orderRepository.findByIdForUpdate(initial.getOrderId()).orElseThrow(()
                -> new ResourceNotFoundException("주문을 찾을 수 없습니다."));
        Payment payment = paymentRepository.findByPgOrderIdForUpdate(pgOrderId).orElseThrow(()
                -> new ResourceNotFoundException("결제 준비 정보를 찾을 수 없습니다."));

        PaymentOrderOwnershipPolicy.validate(order, memberId, guestOrderTokenHash);

        validateRequest(payment, idempotencyKey, paymentKey, amount);

        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            return current(order, payment);
        }

        if (payment.getStatus() == PaymentStatus.FAILED) {
            throw new PaymentGatewayDeclinedException(
                    payment.getFailureCode(),
                    payment.getFailureReason() == null ? "결제 승인이 실패했습니다." : payment.getFailureReason());
        }

        if (payment.getStatus() == PaymentStatus.CANCELLED) {
            throw new ConflictException("취소된 결제 시도입니다. 새 결제 시도를 준비해주세요.");
        }

        if (payment.getStatus() == PaymentStatus.PROCESSING) {
            return current(order, payment);
        }

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new ConflictException("결제할 수 있는 주문 상태가 아닙니다.");
        }

        VirtualPaymentScenario scenario = "VIRTUAL".equals(payment.getPgProvider())
                ? VirtualPaymentScenario.from(rawVirtualScenario)
                : VirtualPaymentScenario.SUCCESS;
        List<OrderItem> orderItems = orderItemRepository.findAllByOrderIdOrderByIdAsc(order.getId());
        PaymentSnapshotValidator.validate(order, orderItems);
        payment.recordPaymentKey(paymentKey);
        inventoryService.reserve(orderItems);
        payment.changeStatus(PaymentStatus.PROCESSING);

        PaymentGatewayCommand command = new PaymentGatewayCommand(
                paymentKey,
                pgOrderId,
                payment.getAmount(),
                payment.getIdempotencyKey(),
                scenario);
        return new PaymentProcessingContext(
                PaymentConfirmResponseMapper.from(order, payment),
                command,
                payment.getPgProvider(),
                true);
    }

    private PaymentProcessingContext current(Order order, Payment payment) {
        return new PaymentProcessingContext(
                PaymentConfirmResponseMapper.from(order, payment),
                null,
                payment.getPgProvider(),
                false);
    }

    private void validateRequest(Payment payment, String idempotencyKey, String paymentKey, Integer amount) {
        if (!payment.getIdempotencyKey().equals(idempotencyKey)) {
            throw new DuplicateException("결제 준비 때 사용한 멱등성 키와 일치하지 않습니다.");
        }

        if (!payment.getAmount().equals(amount)) {
            throw new ConflictException("결제금액이 준비된 주문금액과 일치하지 않습니다.");
        }

        if (payment.getPgPaymentKey() != null && !payment.getPgPaymentKey().equals(paymentKey)) {
            throw new DuplicateException("동일한 결제 시도에 다른 PG 결제키가 사용되었습니다.");
        }
    }
}
