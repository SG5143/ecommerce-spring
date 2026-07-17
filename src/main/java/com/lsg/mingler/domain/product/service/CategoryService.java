package com.lsg.mingler.domain.product.service;

import com.lsg.mingler.domain.product.dao.CategoryRepository;
import com.lsg.mingler.domain.product.dto.CategoryMenu;
import com.lsg.mingler.domain.product.entity.Category;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    /** 헤더 내비게이션 메뉴: 활성 최상위 카테고리와 그 활성 자식들 (displayOrder 순) */
    @Transactional(readOnly = true)
    public List<CategoryMenu> getHeaderCategories() {
        List<Category> actives = categoryRepository.findAllByIsActiveTrueOrderByDisplayOrderAscIdAsc();

        Map<Long, List<Category>> childrenByParent = actives.stream()
                .filter(category -> category.getParentId() != null)
                .collect(Collectors.groupingBy(Category::getParentId,
                        LinkedHashMap::new, Collectors.toList()));

        return actives.stream()
                .filter(category -> category.getParentId() == null)
                .map(root -> new CategoryMenu(root.getId(), root.getName(),
                        childrenByParent.getOrDefault(root.getId(), List.of()).stream()
                                .map(child -> new CategoryMenu(child.getId(), child.getName(), List.of()))
                                .toList()))
                .toList();
    }

}
