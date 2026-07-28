package com.lsg.mingler.domain.payment.dao;

import com.lsg.mingler.domain.payment.entity.Payment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /**
     * 회원 범위에서 멱등성 키가 일치하는 기존 결제를 조회한다.
     *
     * @param memberId 회원 ID
     * @param idempotencyKey 결제 요청 멱등성 키
     * @return 기존 결제가 존재하면 해당 결제, 없으면 빈 값
     */
    Optional<Payment> findByMemberIdAndIdempotencyKey(Long memberId, String idempotencyKey);

    /**
     * 비회원 주문 범위에서 멱등성 키가 일치하는 기존 결제를 조회한다.
     *
     * @param orderId 주문 ID
     * @param idempotencyKey 결제 요청 멱등성 키
     * @return 기존 결제가 존재하면 해당 결제, 없으면 빈 값
     */
    Optional<Payment> findByOrderIdAndIdempotencyKey(Long orderId, String idempotencyKey);

    /**
     * 동일한 결제번호가 이미 존재하는지 확인한다.
     *
     * @param paymentNumber 확인할 결제번호
     * @return 동일한 결제번호가 존재하면 {@code true}
     */
    boolean existsByPaymentNumber(String paymentNumber);

    /**
     * 동일한 PG 거래키가 이미 존재하는지 확인한다.
     *
     * @param pgTransactionKey 확인할 PG 거래키
     * @return 동일한 PG 거래키가 존재하면 {@code true}
     */
    boolean existsByPgTransactionKey(String pgTransactionKey);
}
