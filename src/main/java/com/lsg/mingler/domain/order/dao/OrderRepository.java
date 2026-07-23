package com.lsg.mingler.domain.order.dao;

import com.lsg.mingler.domain.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

    boolean existsByOrderNumber(String orderNumber);

    boolean existsByGuestTokenHash(String guestTokenHash);
}
