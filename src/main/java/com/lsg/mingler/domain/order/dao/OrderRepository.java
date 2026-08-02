package com.lsg.mingler.domain.order.dao;

import com.lsg.mingler.domain.order.entity.Order;
import com.lsg.mingler.domain.order.entity.OrderStatus;
import com.lsg.mingler.domain.payment.entity.PaymentStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * 회원의 주문을 최신 생성순으로 페이지 조회한다.
     */
    Page<Order> findByMemberIdAndStatusNotOrderByCreatedAtDescIdDesc(
            Long memberId, OrderStatus excludedStatus, Pageable pageable);

    /** 결제 기한이 지났고 활성·유효 결제가 없는 결제 대기 주문 ID를 오래된 순서로 조회한다. */
    @Query("""
            SELECT o.id
            FROM Order o
            WHERE o.status = :orderStatus
              AND o.createdAt < :orderCutoff
              AND NOT EXISTS (
                  SELECT p.id
                  FROM Payment p
                  WHERE p.orderId = o.id
                    AND (
                        p.status IN :blockingStatuses
                        OR (p.status = :pendingStatus AND p.createdAt >= :pendingCutoff)
                    )
              )
            ORDER BY o.createdAt ASC, o.id ASC
            """)
    List<Long> findExpirationCandidateIds(
            @Param("orderStatus") OrderStatus orderStatus,
            @Param("orderCutoff") LocalDateTime orderCutoff,
            @Param("pendingStatus") PaymentStatus pendingStatus,
            @Param("pendingCutoff") LocalDateTime pendingCutoff,
            @Param("blockingStatuses") Collection<PaymentStatus> blockingStatuses,
            Pageable pageable);

    /**
     * 동일한 주문번호를 사용하는 주문이 존재하는지 확인한다.
     *
     * @param orderNumber 확인할 주문번호
     * @return 동일한 주문번호가 존재하면 {@code true}
     */
    boolean existsByOrderNumber(String orderNumber);

    /**
     * 동일한 비회원 주문 토큰 해시를 사용하는 주문이 존재하는지 확인한다.
     *
     * @param guestTokenHash 확인할 비회원 주문 토큰 해시
     * @return 동일한 토큰 해시가 존재하면 {@code true}
     */
    boolean existsByGuestTokenHash(String guestTokenHash);

    /**
     * 회원의 집계 대상 주문에 대해 할인과 배송비가 반영된 최종 결제금액을 합산한다.
     *
     * @param memberId 조회할 회원 식별자
     * @param statuses 총 구매 금액에 포함할 주문 상태
     * @return 대상 주문이 없으면 0, 있으면 최종 결제금액 합계
     */
    @Query("""
            SELECT COALESCE(SUM(o.totalAmount), 0L)
            FROM Order o
            WHERE o.memberId = :memberId
              AND o.status IN :statuses
            """)
    long sumTotalAmountByMemberIdAndStatuses(
            @Param("memberId") Long memberId,
            @Param("statuses") Collection<OrderStatus> statuses);

    /**
     * 결제 처리 중 동일 주문의 동시 변경을 막기 위해 주문번호로 주문을 조회하고
     * 비관적 쓰기 잠금을 획득한다.
     *
     * @param orderNumber 조회할 주문번호
     * @return 주문이 존재하면 잠금이 적용된 주문, 존재하지 않으면 빈 값
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.orderNumber = :orderNumber")
    Optional<Order> findByOrderNumberForUpdate(@Param("orderNumber") String orderNumber);

    /**
     * 결제 상태 전이와 재고 처리 중 동일 주문의 동시 변경을 막기 위해 식별자로 주문을 조회하고
     * 비관적 쓰기 잠금을 획득한다. 잠금은 호출한 트랜잭션이 종료될 때까지 유지된다.
     *
     * @param orderId 조회할 주문 식별자
     * @return 주문이 존재하면 잠금이 적용된 주문, 존재하지 않으면 빈 값
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :orderId")
    Optional<Order> findByIdForUpdate(@Param("orderId") Long orderId);
}
