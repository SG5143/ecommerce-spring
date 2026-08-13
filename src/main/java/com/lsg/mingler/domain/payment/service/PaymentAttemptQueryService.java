package com.lsg.mingler.domain.payment.service;

import com.lsg.mingler.domain.order.dao.OrderRepository;
import com.lsg.mingler.domain.order.entity.Order;
import com.lsg.mingler.domain.payment.dao.PaymentRepository;
import com.lsg.mingler.domain.payment.dto.PaymentConfirmResponse;
import com.lsg.mingler.domain.payment.entity.Payment;
import com.lsg.mingler.global.error.AuthenticationException;
import com.lsg.mingler.global.error.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PG 주문번호에 해당하는 결제 시도의 현재 로컬 상태를 조회하고 주문 소유권을 검증한다.
 */
@Service
@RequiredArgsConstructor
public class PaymentAttemptQueryService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;

    /**
     * 회원 ID 또는 비회원 주문 토큰 해시로 소유권을 확인한 뒤 결제와 주문 상태를 반환한다.
     *
     * @param memberId 인증된 회원 ID
     * @param guestOrderTokenHash 비회원 주문 토큰 해시
     * @param pgOrderId 결제 준비 시 발급한 PG 주문번호
     * @return 현재 결제와 주문 상태
     */
    @Transactional(readOnly = true)
    public PaymentConfirmResponse find(Long memberId, String guestOrderTokenHash, String pgOrderId) {
        if (memberId == null && guestOrderTokenHash == null) {
            throw new AuthenticationException("비회원 주문 조회 토큰이 필요합니다.");
        }

        Payment payment = paymentRepository.findByPgOrderId(pgOrderId).orElseThrow(()
                -> new ResourceNotFoundException("결제 정보를 찾을 수 없습니다."));

        Order order = orderRepository.findById(payment.getOrderId()).orElseThrow(()
                -> new ResourceNotFoundException("주문을 찾을 수 없습니다."));

        PaymentOrderOwnershipPolicy.validate(order, memberId, guestOrderTokenHash);
        PaymentOrderAvailabilityPolicy.validate(order);

        return PaymentConfirmResponseMapper.from(order, payment);
    }
}
