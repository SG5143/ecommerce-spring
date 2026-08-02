package com.lsg.mingler.domain.payment.service;

import com.lsg.mingler.domain.order.dao.OrderRepository;
import com.lsg.mingler.domain.order.entity.Order;
import com.lsg.mingler.domain.payment.dao.PaymentRepository;
import com.lsg.mingler.domain.payment.dto.PaymentConfirmResponse;
import com.lsg.mingler.domain.payment.entity.Payment;
import com.lsg.mingler.domain.payment.entity.PaymentStatus;
import com.lsg.mingler.global.error.AuthenticationException;
import com.lsg.mingler.global.error.ConflictException;
import com.lsg.mingler.global.error.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제창 인증 실패 또는 사용자 취소 시 아직 승인 처리를 시작하지 않은 결제 준비 건을 종료한다.
 */
@Service
@RequiredArgsConstructor
public class PaymentPreparationCancelService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;

    /**
     * 주문 소유권을 확인하고 PENDING 결제를 멱등하게 CANCELLED로 전환한다.
     * 이미 PROCESSING인 결제는 승인 결과와 경합하지 않도록 취소하지 않는다.
     *
     * @param memberId 인증된 회원 ID
     * @param guestOrderTokenHash 비회원 주문 토큰 해시
     * @param pgOrderId 취소할 PG 주문번호
     * @return 취소 처리 후 결제와 주문 상태
     */
    @Transactional
    public PaymentConfirmResponse cancel(Long memberId, String guestOrderTokenHash, String pgOrderId) {
        if (memberId == null && guestOrderTokenHash == null) {
            throw new AuthenticationException("비회원 주문 조회 토큰이 필요합니다.");
        }
        Payment initial = paymentRepository.findByPgOrderId(pgOrderId).orElseThrow(()
                -> new ResourceNotFoundException("결제 준비 정보를 찾을 수 없습니다."));
        Order order = orderRepository.findByIdForUpdate(initial.getOrderId()).orElseThrow(()
                -> new ResourceNotFoundException("주문을 찾을 수 없습니다."));
        Payment payment = paymentRepository.findByPgOrderIdForUpdate(pgOrderId).orElseThrow(()
                -> new ResourceNotFoundException("결제 준비 정보를 찾을 수 없습니다."));

        PaymentOrderOwnershipPolicy.validate(order, memberId, guestOrderTokenHash);

        if (payment.getStatus() == PaymentStatus.PENDING) {
            payment.markCancelled("PAY_PROCESS_CANCELED", "결제 인증이 취소되거나 실패했습니다.");
        } else if (payment.getStatus() == PaymentStatus.PROCESSING) {
            throw new ConflictException("이미 결제 승인을 확인하고 있습니다.");
        }

        return PaymentConfirmResponseMapper.from(order, payment);
    }
}
