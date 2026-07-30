package com.lsg.mingler.domain.payment.dao;

import com.lsg.mingler.domain.payment.entity.Payment;
import com.lsg.mingler.domain.payment.entity.PaymentStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
     * 결제 시도별 PG 주문번호로 결제를 조회한다.
     *
     * @param pgOrderId PG에 전달한 주문번호
     * @return 결제가 존재하면 해당 결제, 없으면 빈 값
     */
    Optional<Payment> findByPgOrderId(String pgOrderId);

    /**
     * 결제 상태 전이와 재고 처리를 한 번만 수행하도록 PG 주문번호로 결제를 조회하고
     * 비관적 쓰기 잠금을 획득한다. 잠금은 호출한 트랜잭션이 종료될 때까지 유지된다.
     *
     * @param pgOrderId PG에 전달한 주문번호
     * @return 결제가 존재하면 잠금이 적용된 결제, 없으면 빈 값
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.pgOrderId = :pgOrderId")
    Optional<Payment> findByPgOrderIdForUpdate(@Param("pgOrderId") String pgOrderId);

    /**
     * 동일 주문에서 지정한 상태에 해당하는 가장 최근 결제 시도를 조회한다.
     * 결제 준비 시 PENDING 또는 PROCESSING 상태의 활성 시도가 중복 생성되는 것을 막는 데 사용한다.
     *
     * @param orderId 주문 ID
     * @param statuses 조회할 결제 상태 목록
     * @return 조건에 맞는 가장 최근 결제, 없으면 빈 값
     */
    Optional<Payment> findFirstByOrderIdAndStatusInOrderByCreatedAtDesc(
            Long orderId, Collection<PaymentStatus> statuses);

    /**
     * 지정 시각 이전부터 처리 중이며 수동 검토 사유가 없는 결제를 오래된 순서로 조회한다.
     * 조회 건수는 {@code pageable}로 제한하며 승인 결과 자동 재조정에 사용한다.
     *
     * @param status 조회할 결제 상태
     * @param processingAt 처리 시작 시각의 상한
     * @param pageable 조회 범위와 최대 건수
     * @return 자동 재조정 대상 결제 목록
     */
    List<Payment> findByStatusAndProcessingAtBeforeAndFailureCodeIsNullOrderByProcessingAtAsc(
            PaymentStatus status, LocalDateTime processingAt, Pageable pageable);

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

    /**
     * 동일한 PG 주문번호가 이미 존재하는지 확인한다.
     *
     * @param pgOrderId 확인할 PG 주문번호
     * @return 동일한 PG 주문번호가 존재하면 {@code true}
     */
    boolean existsByPgOrderId(String pgOrderId);

    /**
     * 동일한 PG 결제 키가 이미 저장되어 있는지 확인한다.
     *
     * @param pgPaymentKey 확인할 PG 결제 키
     * @return 동일한 PG 결제 키가 존재하면 {@code true}
     */
    boolean existsByPgPaymentKey(String pgPaymentKey);
}
