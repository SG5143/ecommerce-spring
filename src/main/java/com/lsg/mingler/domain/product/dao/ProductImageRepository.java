package com.lsg.mingler.domain.product.dao;

import com.lsg.mingler.domain.product.entity.ProductImage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    /** 상품 상세: 특정 상품의 이미지를 노출 순서대로 조회 (동률 시 id 순) */
    List<ProductImage> findAllByProductIdOrderByDisplayOrderAscIdAsc(Long productId);

}
