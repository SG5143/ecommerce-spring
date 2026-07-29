package com.lsg.mingler.domain.product.dao;

import com.lsg.mingler.domain.product.entity.ProductOption;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductOptionRepository extends JpaRepository<ProductOption, Long> {

    /** 상품 상세: 특정 상품의 활성 옵션을 노출 순서대로 조회 (동률 시 id 순) */
    List<ProductOption> findAllByProductIdAndIsActiveTrueOrderByDisplayOrderAscIdAsc(Long productId);

    /**
     * 결제할 상품 옵션을 ID 오름차순으로 조회하고 비관적 쓰기 잠금을 획득한다.
     * 잠금 순서를 고정해 동시 결제 시 교착상태 발생 가능성을 줄인다.
     *
     * @param ids 잠금을 획득할 상품 옵션 ID 목록
     * @return ID 오름차순으로 잠긴 상품 옵션 목록
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM ProductOption o WHERE o.id IN :ids ORDER BY o.id")
    List<ProductOption> findAllByIdInForUpdate(@Param("ids") Collection<Long> ids);
}
