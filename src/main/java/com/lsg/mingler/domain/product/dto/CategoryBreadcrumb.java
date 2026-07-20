package com.lsg.mingler.domain.product.dto;

/** 상품 상세 페이지 카테고리 경로: 현재 카테고리와 활성 부모 정보 */
public record CategoryBreadcrumb(Long id, String name, Long parentId, String parentName) {

    public boolean hasParent() {
        return parentName != null;
    }

}
