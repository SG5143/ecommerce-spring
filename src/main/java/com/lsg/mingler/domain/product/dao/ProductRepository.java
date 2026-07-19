package com.lsg.mingler.domain.product.dao;

import com.lsg.mingler.domain.product.entity.Product;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /** 메인화면: 판매중 상품 판매량순 상위 3개 (판매량 동률 시 id 내림차순) */
    List<Product> findTop3ByStatusOrderBySalesCountDescIdDesc(String status);

    /** 메인화면: 판매중 상품 최신순 상위 3개 (등록시각 동률 시 id 내림차순) */
    List<Product> findTop3ByStatusOrderByCreatedAtDescIdDesc(String status);

    /** 카테고리 목록: 해당 카테고리들의 판매중 상품 최신순 (등록시각 동률 시 id 내림차순) */
    List<Product> findByCategoryIdInAndStatusOrderByCreatedAtDescIdDesc(Collection<Long> categoryIds, String status);

}
