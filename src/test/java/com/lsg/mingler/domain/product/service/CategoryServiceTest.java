package com.lsg.mingler.domain.product.service;

import com.lsg.mingler.domain.product.dao.CategoryRepository;
import com.lsg.mingler.domain.product.dto.CategoryMenu;
import com.lsg.mingler.domain.product.entity.Category;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
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

}
