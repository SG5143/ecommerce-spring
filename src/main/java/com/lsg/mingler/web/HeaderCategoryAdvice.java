package com.lsg.mingler.web;

import com.lsg.mingler.domain.product.dto.CategoryMenu;
import com.lsg.mingler.domain.product.service.CategoryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** 헤더를 쓰는 모든 뷰 페이지에 카테고리 메뉴를 공급 (web 패키지 뷰 컨트롤러 한정) */
@ControllerAdvice(basePackages = "com.lsg.mingler.web")
@RequiredArgsConstructor
public class HeaderCategoryAdvice {

    private final CategoryService categoryService;

    @ModelAttribute("headerCategories")
    public List<CategoryMenu> headerCategories() {
        return categoryService.getHeaderCategories();
    }

}
