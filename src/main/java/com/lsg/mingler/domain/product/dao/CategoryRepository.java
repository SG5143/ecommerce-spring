package com.lsg.mingler.domain.product.dao;

import com.lsg.mingler.domain.product.dto.CategoryBreadcrumb;
import com.lsg.mingler.domain.product.entity.Category;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    /** 활성 카테고리 전체를 노출 순서대로 조회 (동률 시 id 순) */
    List<Category> findAllByIsActiveTrueOrderByDisplayOrderAscIdAsc();

    /** 활성 카테고리 단건 조회 */
    Optional<Category> findByIdAndIsActiveTrue(Long id);

    /** 특정 부모의 활성 자식 카테고리를 노출 순서대로 조회 (동률 시 id 순) */
    List<Category> findAllByParentIdAndIsActiveTrueOrderByDisplayOrderAscIdAsc(Long parentId);

    /** 상품 상세 브레드크럼: 현재 활성 카테고리와 활성 부모를 한 번에 조회 */
    @Query("""
            SELECT new com.lsg.mingler.domain.product.dto.CategoryBreadcrumb(
                category.id, category.name, category.parentId, parent.name
            )
            FROM Category category
            LEFT JOIN Category parent
                ON parent.id = category.parentId AND parent.isActive = true
            WHERE category.id = :id AND category.isActive = true
            """)
    Optional<CategoryBreadcrumb> findBreadcrumbById(@Param("id") Long id);

}
