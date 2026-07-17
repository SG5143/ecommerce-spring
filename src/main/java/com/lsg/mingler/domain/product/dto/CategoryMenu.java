package com.lsg.mingler.domain.product.dto;

import java.util.List;

/** 헤더 내비게이션용 카테고리 메뉴 항목 */
public record CategoryMenu(Long id, String name, List<CategoryMenu> children) {
}
