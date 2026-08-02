package com.lsg.mingler.domain.payment.service;

import com.lsg.mingler.domain.order.dao.OrderItemRepository;
import com.lsg.mingler.domain.order.dao.OrderRepository;
import com.lsg.mingler.domain.order.entity.Order;
import com.lsg.mingler.domain.order.entity.OrderItem;
import com.lsg.mingler.domain.order.entity.OrderStatus;
import com.lsg.mingler.domain.payment.dao.PaymentRepository;
import com.lsg.mingler.domain.payment.dto.PaymentPrepareResponse;
import com.lsg.mingler.domain.payment.entity.Payment;
import com.lsg.mingler.domain.payment.entity.PaymentStatus;
import com.lsg.mingler.global.error.ConflictException;
import com.lsg.mingler.global.error.DuplicateException;
import com.lsg.mingler.global.error.ResourceNotFoundException;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문을 잠그고 스냅샷 금액과 활성 결제 시도를 검증한 뒤 PENDING 결제를 생성하는 트랜잭션 서비스다.
 */
@Service
@RequiredArgsConstructor
class PaymentPreparationTransactionService {

    private static final int IDENTIFIER_GENERATION_ATTEMPTS = 5;
    private static final EnumSet<PaymentStatus> ACTIVE_STATUSES = EnumSet.of(PaymentStatus.PENDING, PaymentStatus.PROCESSING);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentIdentifierGenerator identifierGenerator;
    private final PaymentGatewayResolver gatewayResolver;
    private final PaymentProperties properties;

    /**
     * 동일 멱등성 키에는 기존 준비 결과를 반환하고, 새 요청에는 고유한 PG 주문번호를 발급한다.
     * 이 단계에서는 재고를 차감하지 않는다.
     *
     * @param memberId 인증된 회원 ID
     * @param guestOrderTokenHash 비회원 주문 토큰 해시
     * @param orderNumber 결제할 Mingler 주문번호
     * @param paymentMethod 검증된 결제수단
     * @param paymentProvider 검증된 결제 제공자
     * @param idempotencyKey 결제 준비 멱등성 키
     * @return 결제창 호출에 필요한 준비 결과
     */
    @Transactional
    PaymentPrepareResponse prepare(
            Long memberId,
            String guestOrderTokenHash,
            String orderNumber,
            String paymentMethod,
            String paymentProvider,
            String idempotencyKey) {
        Order order = orderRepository.findByOrderNumberForUpdate(orderNumber).orElseThrow(()
                -> new ResourceNotFoundException("주문을 찾을 수 없습니다."));
        PaymentOrderOwnershipPolicy.validate(order, memberId, guestOrderTokenHash);

        Optional<Payment> existing = findExisting(order.getId(), memberId, idempotencyKey);
        if (existing.isPresent()) {
            validateSamePreparation(order, existing.get(), paymentMethod, paymentProvider);
            return toResponse(order, existing.get(), orderItems(order));
        }

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new ConflictException("결제를 준비할 수 있는 주문 상태가 아닙니다.");
        }

        Optional<Payment> active = paymentRepository.findFirstByOrderIdAndStatusInOrderByCreatedAtDesc(order.getId(), ACTIVE_STATUSES);
        if (active.isPresent()) {
            Payment activePayment = active.get();
            if (isExpiredPending(activePayment)) {
                activePayment.markCancelled("PREPARATION_EXPIRED", "결제 준비 유효시간이 만료되었습니다.");
            } else {
                throw new ConflictException("동일 주문에 진행 중인 결제 시도가 있습니다.");
            }
        }

        List<OrderItem> orderItems = orderItems(order);
        PaymentSnapshotValidator.validate(order, orderItems);
        Payment payment = Payment.builder()
                .paymentNumber(generatePaymentNumber())
                .orderId(order.getId())
                .memberId(memberId)
                .idempotencyKey(idempotencyKey)
                .pgProvider(paymentProvider)
                .paymentMethod(paymentMethod)
                .amount(order.getTotalAmount())
                .build();

        payment.recordPreparation(generatePgOrderId(paymentProvider));
        paymentRepository.saveAndFlush(payment);

        return toResponse(order, payment, orderItems);
    }

    private Optional<Payment> findExisting(Long orderId, Long memberId, String idempotencyKey) {
        if (memberId != null) {
            return paymentRepository.findByMemberIdAndIdempotencyKey(memberId, idempotencyKey);
        }
        return paymentRepository.findByOrderIdAndIdempotencyKey(orderId, idempotencyKey);
    }

    private void validateSamePreparation(
            Order order,
            Payment payment,
            String paymentMethod,
            String paymentProvider) {
        boolean same = order.getId().equals(payment.getOrderId())
                && order.getTotalAmount().equals(payment.getAmount())
                && paymentMethod.equals(payment.getPaymentMethod())
                && paymentProvider.equals(payment.getPgProvider())
                && payment.getPgOrderId() != null;
        if (!same) {
            throw new DuplicateException("동일한 멱등성 키의 결제 준비 정보가 이전 요청과 다릅니다.");
        }
    }

    private boolean isExpiredPending(Payment payment) {
        return payment.getStatus() == PaymentStatus.PENDING
                && payment.getCreatedAt() != null
                && payment.getCreatedAt().isBefore(
                LocalDateTime.now().minus(properties.reconciliation().preparationTtl()));
    }

    private List<OrderItem> orderItems(Order order) {
        return orderItemRepository.findAllByOrderIdOrderByIdAsc(order.getId());
    }

    private PaymentPrepareResponse toResponse(Order order, Payment payment, List<OrderItem> orderItems) {
        return new PaymentPrepareResponse(
                gatewayResolver.clientKey(payment.getPgProvider()),
                payment.getPgOrderId(),
                createOrderName(orderItems),
                order.getTotalAmount(),
                "KRW",
                identifierGenerator.customerKeyFrom(payment.getPgOrderId()),
                payment.getPgProvider());
    }

    private String createOrderName(List<OrderItem> orderItems) {
        if (orderItems.isEmpty()) {
            throw new ConflictException("주문 상품 스냅샷이 없습니다.");
        }
        String suffix = orderItems.size() > 1 ? " 외 " + (orderItems.size() - 1) + "건" : "";
        int nameLength = Math.min(orderItems.getFirst().getProductName().length(), 100 - suffix.length());
        return orderItems.getFirst().getProductName().substring(0, nameLength) + suffix;
    }

    private String generatePaymentNumber() {
        for (int attempt = 0; attempt < IDENTIFIER_GENERATION_ATTEMPTS; attempt++) {
            String candidate = identifierGenerator.generatePaymentNumber();
            if (!paymentRepository.existsByPaymentNumber(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("결제번호를 생성할 수 없습니다.");
    }

    private String generatePgOrderId(String provider) {
        for (int attempt = 0; attempt < IDENTIFIER_GENERATION_ATTEMPTS; attempt++) {
            String candidate = identifierGenerator.generatePgOrderId(provider);
            if (!paymentRepository.existsByPgOrderId(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("PG 주문번호를 생성할 수 없습니다.");
    }
}
