package com.lsg.mingler.web;

import com.lsg.mingler.domain.payment.service.PaymentGatewayResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class CheckoutViewController {

    private final PaymentGatewayResolver paymentGatewayResolver;

    @GetMapping("/checkout")
    public String order() {
        return "checkout/order";
    }

    @GetMapping("/checkout/payment")
    public String payment(Model model) {
        model.addAttribute("tossPaymentAvailable", paymentGatewayResolver.isAvailable("TOSS"));
        return "checkout/payment";
    }

    @GetMapping("/checkout/payment/success")
    public String paymentSuccess() {
        return "checkout/payment-success";
    }

    @GetMapping("/checkout/payment/fail")
    public String paymentFail() {
        return "checkout/payment-fail";
    }

    @GetMapping("/checkout/complete")
    public String complete() {
        return "checkout/complete";
    }
}
