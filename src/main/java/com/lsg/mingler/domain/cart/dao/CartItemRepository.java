package com.lsg.mingler.domain.cart.dao;

import com.lsg.mingler.domain.cart.entity.CartItem;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    List<CartItem> findAllByCartIdOrderByCreatedAtAscIdAsc(Long cartId);

    List<CartItem> findAllByCartIdAndIdIn(Long cartId, Collection<Long> ids);

    Optional<CartItem> findByCartIdAndId(Long cartId, Long id);

    /** 헤더 장바구니 배지에 표시할 전체 상품 수량 합계를 조회한다. */
    @Query("SELECT COALESCE(SUM(i.quantity), 0) FROM CartItem i WHERE i.cartId = :cartId")
    long sumQuantityByCartId(@Param("cartId") Long cartId);

    @Query("""
            SELECT i FROM CartItem i
            WHERE i.cartId = :cartId AND i.productId = :productId
              AND i.productOptionId = :optionId
            """)
    Optional<CartItem> findVariant(@Param("cartId") Long cartId,
                                   @Param("productId") Long productId,
                                   @Param("optionId") Long optionId);

    @Modifying
    long deleteAllByCartIdAndIdIn(Long cartId, Collection<Long> ids);
}
