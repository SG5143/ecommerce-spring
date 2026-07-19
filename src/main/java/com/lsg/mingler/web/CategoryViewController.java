package com.lsg.mingler.web;

import com.lsg.mingler.domain.product.dto.CategoryPage;
import com.lsg.mingler.domain.product.service.CategoryService;
import com.lsg.mingler.domain.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
@RequiredArgsConstructor
public class CategoryViewController {

    private final CategoryService categoryService;
    private final ProductService productService;

    @GetMapping("/categories/{id}")
    public String category(@PathVariable Long id, Model model) {
        CategoryPage category = categoryService.getCategoryPage(id);
        model.addAttribute("category", category);
        model.addAttribute("products", productService.getCategoryProducts(category.productCategoryIds()));
        return "category/category";
    }

}
