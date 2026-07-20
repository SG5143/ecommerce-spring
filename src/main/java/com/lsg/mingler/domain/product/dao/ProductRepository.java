package com.lsg.mingler.domain.product.dao;

import com.lsg.mingler.domain.product.entity.Product;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /** 메인화면: 판매중 상품 판매량순 상위 3개 (판매량 동률 시 id 내림차순) */
    List<Product> findTop3ByStatusOrderBySalesCountDescIdDesc(String status);

    /** 메인화면: 판매중 상품 최신순 상위 3개 (등록시각 동률 시 id 내림차순) */
    List<Product> findTop3ByStatusOrderByCreatedAtDescIdDesc(String status);

    /** 카테고리 목록: 해당 카테고리들의 판매중 상품 최신순 (등록시각 동률 시 id 내림차순) */
    List<Product> findByCategoryIdInAndStatusOrderByCreatedAtDescIdDesc(Collection<Long> categoryIds, String status);

    /** 상품 상세: 숨김 상품을 제외하고 조회수를 원자적으로 1 증가 */
    @Modifying
    @Query("""
            UPDATE Product p
            SET p.viewCount = p.viewCount + 1
            WHERE p.id = :id AND p.status <> :hiddenStatus
            """)
    int increaseViewCount(@Param("id") Long id, @Param("hiddenStatus") String hiddenStatus);

}
