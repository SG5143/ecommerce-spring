package com.lsg.mingler.domain.payment.service;

import com.lsg.mingler.domain.order.dao.OrderRepository;
import com.lsg.mingler.domain.order.entity.Order;
import com.lsg.mingler.domain.payment.dao.PaymentRepository;
import com.lsg.mingler.domain.payment.dto.PaymentConfirmResponse;
import com.lsg.mingler.domain.payment.entity.Payment;
import com.lsg.mingler.domain.payment.entity.PaymentStatus;
import com.lsg.mingler.global.error.ConflictException;
import com.lsg.mingler.global.error.DuplicateException;
import com.lsg.mingler.global.error.PaymentApprovalException;
import com.lsg.mingler.global.error.ResourceNotFoundException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentFailureTransactionService {

    private static final int IDENTIFIER_GENERATION_ATTEMPTS = 5;
    private static final String PG_PROVIDER = "VIRTUAL";

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentIdentifierGenerator identifierGenerator;

    /**
     * 승인 트랜잭션 롤백이 끝난 뒤 독립 트랜잭션에서 실패 시도만 기록한다.
     * 동일 멱등성 키의 결제가 이미 있으면 중복 기록하지 않는다.
     *
     * @param memberId 인증된 회원 ID이며 비회원 요청이면 {@code null}
     * @param guestOrderTokenHash 비회원 주문 토큰 해시이며 회원 요청이면 {@code null}
     * @param command 검증과 정규화가 완료된 결제 승인 명령
     * @param failure 가상 PG에서 확정된 승인 실패
     * @return 기존 성공 결제가 있으면 재응답, 실패 이력을 기록했거나 이미 실패했으면 빈 값
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    Optional<PaymentConfirmResponse> recordFailure(Long memberId, String guestOrderTokenHash, PaymentService.PaymentConfirmCommand command, PaymentApprovalException failure) {
        Order order = orderRepository.findByOrderNumberForUpdate(command.orderNumber()).orElseThrow(()
                -> new ResourceNotFoundException("주문을 찾을 수 없습니다."));
        PaymentOrderOwnershipPolicy.validate(order, memberId, guestOrderTokenHash);
        PaymentOrderAvailabilityPolicy.validate(order);

        Optional<Payment> existing = findExistingPayment(order.getId(), memberId, command.idempotencyKey());
        if (existing.isPresent()) {
            return resolveExisting(order, existing.get(), command, failure);
        }

        Payment payment = Payment.builder()
                .paymentNumber(generatePaymentNumber())
                .orderId(order.getId())
                .memberId(memberId)
                .idempotencyKey(command.idempotencyKey())
                .pgProvider(PG_PROVIDER)
                .paymentMethod(command.paymentMethod())
                .amount(command.amount())
                .build();
        payment.recordFailure(failure.getFailureCode(), failure.getMessage());
        payment.changeStatus(PaymentStatus.PROCESSING);
        payment.changeStatus(PaymentStatus.FAILED);
        paymentRepository.saveAndFlush(payment);
        return Optional.empty();
    }

    /**
     * UNIQUE 제약 경합으로 다른 트랜잭션이 먼저 저장한 결제를 새 트랜잭션에서 재조회한다.
     *
     * @return 기존 성공 결제가 있으면 재응답, 기존 실패면 빈 값
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    Optional<PaymentConfirmResponse> resolveExistingPayment(Long memberId, String guestOrderTokenHash, PaymentService.PaymentConfirmCommand command, PaymentApprovalException failure) {
        Order order = orderRepository.findByOrderNumberForUpdate(command.orderNumber()).orElseThrow(()
                -> new ResourceNotFoundException("주문을 찾을 수 없습니다."));
        PaymentOrderOwnershipPolicy.validate(order, memberId, guestOrderTokenHash);
        PaymentOrderAvailabilityPolicy.validate(order);
        Payment existing = findExistingPayment(order.getId(), memberId, command.idempotencyKey())
                .orElseThrow(() -> new IllegalStateException("멱등성 키 경합 후 기존 결제를 찾을 수 없습니다."));
        return resolveExisting(order, existing, command, failure);
    }

    private Optional<PaymentConfirmResponse> resolveExisting(
            Order order,
            Payment existing,
            PaymentService.PaymentConfirmCommand command,
            PaymentApprovalException failure) {
        PaymentRequestPolicy.validateSameRequest(order, existing, command);

        if (existing.getStatus() == PaymentStatus.SUCCESS) {
            log.warn(
                    "실패 기록 경합 중 기존 성공 결제를 재응답합니다. orderNumber={}, paymentNumber={}",
                    order.getOrderNumber(),
                    existing.getPaymentNumber());
            return Optional.of(PaymentConfirmResponseMapper.from(order, existing));
        }
        if (existing.getStatus() == PaymentStatus.FAILED) {
            if (!failure.getFailureCode().equals(existing.getFailureCode())) {
                throw new DuplicateException("동일한 멱등성 키에 서로 다른 결제 실패 결과가 기록되어 있습니다.");
            }
            log.info(
                    "동일한 결제 실패 이력이 이미 기록되어 있습니다. orderNumber={}, paymentNumber={}, failureCode={}",
                    order.getOrderNumber(),
                    existing.getPaymentNumber(),
                    existing.getFailureCode());
            return Optional.empty();
        }
        throw new ConflictException("동일한 결제 요청이 처리 중이거나 완료되지 않았습니다.");
    }

    private Optional<Payment> findExistingPayment(Long orderId, Long memberId, String idempotencyKey) {
        if (memberId != null) {
            return paymentRepository.findByMemberIdAndIdempotencyKey(memberId, idempotencyKey);
        }
        return paymentRepository.findByOrderIdAndIdempotencyKey(orderId, idempotencyKey);
    }

    private String generatePaymentNumber() {
        for (int attempt = 0; attempt < IDENTIFIER_GENERATION_ATTEMPTS; attempt++) {
            String paymentNumber = identifierGenerator.generatePaymentNumber();
            if (!paymentRepository.existsByPaymentNumber(paymentNumber)) {
                return paymentNumber;
            }
        }
        throw new IllegalStateException("결제번호를 생성할 수 없습니다.");
    }
}
