package com.lsg.mingler.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class CartViewController {

    @GetMapping("/cart")
    public String cart() {
        return "cart/cart";
    }
}
