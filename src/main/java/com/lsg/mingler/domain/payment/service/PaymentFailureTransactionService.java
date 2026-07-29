package com.lsg.mingler.domain.payment.service;

import com.lsg.mingler.domain.order.dao.OrderRepository;
import com.lsg.mingler.domain.order.entity.Order;
import com.lsg.mingler.domain.payment.dao.PaymentRepository;
import com.lsg.mingler.domain.payment.entity.Payment;
import com.lsg.mingler.domain.payment.entity.PaymentStatus;
import com.lsg.mingler.global.error.PaymentApprovalException;
import com.lsg.mingler.global.error.ResourceNotFoundException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
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
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void recordFailure(Long memberId, String guestOrderTokenHash, PaymentService.PaymentConfirmCommand command, PaymentApprovalException failure) {
        Order order = orderRepository.findByOrderNumberForUpdate(command.orderNumber()).orElseThrow(()
                -> new ResourceNotFoundException("주문을 찾을 수 없습니다."));
        validateOwner(order, memberId, guestOrderTokenHash);

        Optional<Payment> existing = findExistingPayment(order.getId(), memberId, command.idempotencyKey());
        if (existing.isPresent()) {
            return;
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
        payment.changeStatus(PaymentStatus.FAILED);
        paymentRepository.save(payment);
    }

    private void validateOwner(Order order, Long memberId, String guestOrderTokenHash) {
        if (memberId != null) {
            if (!memberId.equals(order.getMemberId())) {
                throw new ResourceNotFoundException("주문을 찾을 수 없습니다.");
            }
            return;
        }
        if (guestOrderTokenHash == null || !guestOrderTokenHash.equals(order.getGuestTokenHash())) {
            throw new ResourceNotFoundException("주문을 찾을 수 없습니다.");
        }
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
