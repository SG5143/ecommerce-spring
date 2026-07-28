package com.lsg.mingler.domain.order.dao;

import com.lsg.mingler.domain.order.entity.OrderItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    /**
     * 지정한 주문의 상품 스냅샷을 ID 오름차순으로 조회한다.
     *
     * @param orderId 주문 ID
     * @return 주문에 포함된 상품 스냅샷 목록
     */
    List<OrderItem> findAllByOrderIdOrderByIdAsc(Long orderId);

}
