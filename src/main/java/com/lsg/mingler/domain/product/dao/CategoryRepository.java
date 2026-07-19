package com.lsg.mingler.domain.product.dao;

import com.lsg.mingler.domain.product.entity.Category;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    /** 활성 카테고리 전체를 노출 순서대로 조회 (동률 시 id 순) */
    List<Category> findAllByIsActiveTrueOrderByDisplayOrderAscIdAsc();

    /** 활성 카테고리 단건 조회 */
    Optional<Category> findByIdAndIsActiveTrue(Long id);

    /** 특정 부모의 활성 자식 카테고리를 노출 순서대로 조회 (동률 시 id 순) */
    List<Category> findAllByParentIdAndIsActiveTrueOrderByDisplayOrderAscIdAsc(Long parentId);

}
