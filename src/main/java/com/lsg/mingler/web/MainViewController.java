package com.lsg.mingler.web;

import com.lsg.mingler.web.dto.ProductCard;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class MainViewController {

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("products", List.of(
                new ProductCard("캐시미어 니트", 89000, "https://placehold.co/300x300?text=Knit"),
                new ProductCard("데일리 백팩", 65000, "https://placehold.co/300x300?text=Backpack"),
                new ProductCard("클래식 스니커즈", 79000, "https://placehold.co/300x300?text=Sneakers"),
                new ProductCard("울 코트", 158000, "https://placehold.co/300x300?text=Coat"),
                new ProductCard("레더 벨트", 39000, "https://placehold.co/300x300?text=Belt"),
                new ProductCard("미니 크로스백", 72000, "https://placehold.co/300x300?text=Crossbag"),
                new ProductCard("와이드 팬츠", 49000, "https://placehold.co/300x300?text=Pants"),
                new ProductCard("스트라이프 셔츠", 45000, "https://placehold.co/300x300?text=Shirt")
        ));
        return "index";
    }

}
