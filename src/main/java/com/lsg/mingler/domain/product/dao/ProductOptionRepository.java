package com.lsg.mingler.domain.product.dao;

import com.lsg.mingler.domain.product.entity.ProductOption;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductOptionRepository extends JpaRepository<ProductOption, Long> {

    /** 상품 상세: 특정 상품의 활성 옵션을 노출 순서대로 조회 (동률 시 id 순) */
    List<ProductOption> findAllByProductIdAndIsActiveTrueOrderByDisplayOrderAscIdAsc(Long productId);

}
