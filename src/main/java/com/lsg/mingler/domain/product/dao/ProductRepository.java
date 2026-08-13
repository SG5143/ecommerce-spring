package com.lsg.mingler.domain.product.dao;

import com.lsg.mingler.domain.product.entity.Product;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    /**
     * 결제할 상품을 ID 오름차순으로 조회하고 비관적 쓰기 잠금을 획득한다.
     * 잠금 순서를 고정해 동시 결제 시 교착상태 발생 가능성을 줄인다.
     *
     * @param ids 잠금을 획득할 상품 ID 목록
     * @return ID 오름차순으로 잠긴 상품 목록
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id IN :ids ORDER BY p.id")
    List<Product> findAllByIdInForUpdate(@Param("ids") Collection<Long> ids);
}
