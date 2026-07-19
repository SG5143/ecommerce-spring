package com.lsg.mingler.domain.product.dto;

import java.util.List;

/** 카테고리 목록 페이지 뷰 모델: 브레드크럼 정보와 상품 조회 대상 카테고리 id 목록 */
public record CategoryPage(Long id, String name, Long parentId, String parentName,
                           List<Long> productCategoryIds) {

    /** 브레드크럼에 부모 경로를 표시할지 여부 */
    public boolean hasParent() {
        return parentName != null;
    }

}
