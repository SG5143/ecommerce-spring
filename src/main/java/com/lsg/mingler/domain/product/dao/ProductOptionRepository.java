package com.lsg.mingler.domain.product.dao;

import com.lsg.mingler.domain.product.entity.ProductOption;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductOptionRepository extends JpaRepository<ProductOption, Long> {

    boolean existsByProductId(Long productId);

    /** 장바구니 응답: 옵션 보유 여부를 상품별로 반복 조회하지 않고 한 번에 확인 */
    @Query("SELECT DISTINCT o.productId FROM ProductOption o WHERE o.productId IN :productIds")
    List<Long> findProductIdsWithOptions(@Param("productIds") Collection<Long> productIds);

    /** 상품 상세: 특정 상품의 활성 옵션을 노출 순서대로 조회 (동률 시 id 순) */
    List<ProductOption> findAllByProductIdAndIsActiveTrueOrderByDisplayOrderAscIdAsc(Long productId);

}
