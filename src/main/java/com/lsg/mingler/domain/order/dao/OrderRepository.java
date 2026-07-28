package com.lsg.mingler.domain.order.dao;

import com.lsg.mingler.domain.order.entity.Order;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {

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
     * 결제 처리 중 동일 주문의 동시 변경을 막기 위해 주문번호로 주문을 조회하고
     * 비관적 쓰기 잠금을 획득한다.
     *
     * @param orderNumber 조회할 주문번호
     * @return 주문이 존재하면 잠금이 적용된 주문, 존재하지 않으면 빈 값
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.orderNumber = :orderNumber")
    Optional<Order> findByOrderNumberForUpdate(@Param("orderNumber") String orderNumber);
}
