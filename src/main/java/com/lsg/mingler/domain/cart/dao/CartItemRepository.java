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

    Optional<CartItem> findByCartIdAndId(Long cartId, Long id);

    @Query("""
            SELECT i FROM CartItem i
            WHERE i.cartId = :cartId AND i.productId = :productId
              AND ((:optionId IS NULL AND i.productOptionId IS NULL) OR i.productOptionId = :optionId)
            """)
    Optional<CartItem> findVariant(@Param("cartId") Long cartId,
                                   @Param("productId") Long productId,
                                   @Param("optionId") Long optionId);

    @Modifying
    long deleteAllByCartIdAndIdIn(Long cartId, Collection<Long> ids);
}
