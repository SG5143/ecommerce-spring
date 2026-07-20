package com.lsg.mingler.web;

import com.lsg.mingler.domain.product.dto.ProductDetail;
import com.lsg.mingler.domain.product.service.CategoryService;
import com.lsg.mingler.domain.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
@RequiredArgsConstructor
public class ProductViewController {

    private final ProductService productService;
    private final CategoryService categoryService;

    @GetMapping("/products/{id}")
    public String detail(@PathVariable Long id, Model model) {
        ProductDetail product = productService.getProductDetail(id);
        model.addAttribute("product", product);
        model.addAttribute("category", categoryService.getCategoryPage(product.categoryId()));
        return "product/detail";
    }

}
