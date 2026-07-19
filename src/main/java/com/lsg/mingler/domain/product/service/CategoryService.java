package com.lsg.mingler.domain.product.service;

import com.lsg.mingler.domain.product.dao.CategoryRepository;
import com.lsg.mingler.domain.product.dto.CategoryMenu;
import com.lsg.mingler.domain.product.dto.CategoryPage;
import com.lsg.mingler.domain.product.entity.Category;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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

    /** 카테고리 목록 페이지: 브레드크럼 정보와 상품 조회 대상(자신 + 활성 자식) id 구성 */
    @Transactional(readOnly = true)
    public CategoryPage getCategoryPage(Long id) {
        Category category = categoryRepository.findByIdAndIsActiveTrue(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "카테고리를 찾을 수 없습니다."));

        String parentName = category.getParentId() == null ? null
                : categoryRepository.findByIdAndIsActiveTrue(category.getParentId())
                        .map(Category::getName)
                        .orElse(null);

        List<Long> productCategoryIds = Stream.concat(
                        Stream.of(category.getId()),
                        categoryRepository.findAllByParentIdAndIsActiveTrueOrderByDisplayOrderAscIdAsc(category.getId())
                                .stream()
                                .map(Category::getId))
                .toList();

        return new CategoryPage(category.getId(), category.getName(),
                category.getParentId(), parentName, productCategoryIds);
    }

}
