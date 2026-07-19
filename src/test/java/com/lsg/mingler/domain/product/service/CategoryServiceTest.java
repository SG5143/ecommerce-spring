package com.lsg.mingler.domain.product.service;

import com.lsg.mingler.domain.product.dao.CategoryRepository;
import com.lsg.mingler.domain.product.dto.CategoryMenu;
import com.lsg.mingler.domain.product.dto.CategoryPage;
import com.lsg.mingler.domain.product.entity.Category;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryService categoryService;

    private Category category(Long id, Long parentId, String name, int displayOrder) {
        Category category = Category.builder()
                .parentId(parentId)
                .name(name)
                .displayOrder(displayOrder)
                .build();
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }

    @Test
    void 최상위_카테고리가_메뉴_루트로_구성된다() {
        when(categoryRepository.findAllByIsActiveTrueOrderByDisplayOrderAscIdAsc())
                .thenReturn(List.of(
                        category(1L, null, "의류", 1),
                        category(2L, null, "잡화", 2),
                        category(3L, 1L, "상의", 1)));

        List<CategoryMenu> menus = categoryService.getHeaderCategories();

        assertThat(menus).hasSize(2);
        assertThat(menus.get(0).id()).isEqualTo(1L);
        assertThat(menus.get(0).name()).isEqualTo("의류");
        assertThat(menus.get(1).name()).isEqualTo("잡화");
    }

    @Test
    void 자식_카테고리가_부모의_children으로_들어간다() {
        when(categoryRepository.findAllByIsActiveTrueOrderByDisplayOrderAscIdAsc())
                .thenReturn(List.of(
                        category(1L, null, "의류", 1),
                        category(3L, 1L, "상의", 1),
                        category(4L, 1L, "하의", 2)));

        List<CategoryMenu> menus = categoryService.getHeaderCategories();

        assertThat(menus.get(0).children()).hasSize(2);
        assertThat(menus.get(0).children().get(0).name()).isEqualTo("상의");
        assertThat(menus.get(0).children().get(1).name()).isEqualTo("하의");
    }

    @Test
    void 쿼리가_반환한_displayOrder_순서가_메뉴에_보존된다() {
        when(categoryRepository.findAllByIsActiveTrueOrderByDisplayOrderAscIdAsc())
                .thenReturn(List.of(
                        category(2L, null, "잡화", 1),
                        category(1L, null, "의류", 2),
                        category(6L, 2L, "신발", 1),
                        category(5L, 2L, "가방", 2)));

        List<CategoryMenu> menus = categoryService.getHeaderCategories();

        assertThat(menus).extracting(CategoryMenu::name).containsExactly("잡화", "의류");
        assertThat(menus.get(0).children()).extracting(CategoryMenu::name).containsExactly("신발", "가방");
    }

    @Test
    void 부모가_비활성이면_그_자식은_노출되지_않는다() {
        when(categoryRepository.findAllByIsActiveTrueOrderByDisplayOrderAscIdAsc())
                .thenReturn(List.of(
                        category(1L, null, "의류", 1),
                        category(3L, 1L, "상의", 1),
                        category(5L, 2L, "가방", 1)));

        List<CategoryMenu> menus = categoryService.getHeaderCategories();

        assertThat(menus).hasSize(1);
        assertThat(menus.get(0).children()).extracting(CategoryMenu::name).containsExactly("상의");
    }

    @Test
    void 자식이_없는_최상위는_빈_children을_가진다() {
        when(categoryRepository.findAllByIsActiveTrueOrderByDisplayOrderAscIdAsc())
                .thenReturn(List.of(category(1L, null, "의류", 1)));

        List<CategoryMenu> menus = categoryService.getHeaderCategories();

        assertThat(menus.get(0).children()).isEmpty();
    }

    @Test
    void 활성_카테고리가_없으면_빈_목록을_반환한다() {
        when(categoryRepository.findAllByIsActiveTrueOrderByDisplayOrderAscIdAsc())
                .thenReturn(List.of());

        List<CategoryMenu> menus = categoryService.getHeaderCategories();

        assertThat(menus).isEmpty();
        verify(categoryRepository).findAllByIsActiveTrueOrderByDisplayOrderAscIdAsc();
    }

    @Test
    void 리프_카테고리면_부모명을_담고_자기_id만_상품_조회_대상이_된다() {
        when(categoryRepository.findByIdAndIsActiveTrue(3L))
                .thenReturn(Optional.of(category(3L, 1L, "상의", 1)));
        when(categoryRepository.findByIdAndIsActiveTrue(1L))
                .thenReturn(Optional.of(category(1L, null, "의류", 1)));
        when(categoryRepository.findAllByParentIdAndIsActiveTrueOrderByDisplayOrderAscIdAsc(3L))
                .thenReturn(List.of());

        CategoryPage page = categoryService.getCategoryPage(3L);

        assertThat(page.name()).isEqualTo("상의");
        assertThat(page.parentId()).isEqualTo(1L);
        assertThat(page.parentName()).isEqualTo("의류");
        assertThat(page.hasParent()).isTrue();
        assertThat(page.productCategoryIds()).containsExactly(3L);
    }

    @Test
    void 최상위_카테고리면_부모명이_없고_자식_id들이_상품_조회_대상에_포함된다() {
        when(categoryRepository.findByIdAndIsActiveTrue(1L))
                .thenReturn(Optional.of(category(1L, null, "의류", 1)));
        when(categoryRepository.findAllByParentIdAndIsActiveTrueOrderByDisplayOrderAscIdAsc(1L))
                .thenReturn(List.of(category(3L, 1L, "상의", 1), category(4L, 1L, "하의", 2)));

        CategoryPage page = categoryService.getCategoryPage(1L);

        assertThat(page.parentName()).isNull();
        assertThat(page.hasParent()).isFalse();
        assertThat(page.productCategoryIds()).containsExactly(1L, 3L, 4L);
    }

    @Test
    void 없거나_비활성인_카테고리면_404_예외를_던진다() {
        when(categoryRepository.findByIdAndIsActiveTrue(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.getCategoryPage(99L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

}
