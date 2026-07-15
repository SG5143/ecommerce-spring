package com.lsg.mingler.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MyShopViewController {

    @GetMapping("/myshop")
    public String myShop() {
        return "myshop/myshop";
    }

    // 서브 페이지는 구현할 때마다 아래처럼 하나씩 추가
    // @GetMapping("/myshop/member-info")
    // public String memberInfo() { return "myshop/member-info"; }

}
