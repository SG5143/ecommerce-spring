package com.lsg.mingler.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class CheckoutViewController {

    @GetMapping("/checkout")
    public String order() {
        return "checkout/order";
    }

    @GetMapping("/checkout/payment")
    public String payment() {
        return "checkout/payment";
    }

    @GetMapping("/checkout/complete")
    public String complete() {
        return "checkout/complete";
    }
}
