package com.lsg.mingler.web;

import com.lsg.mingler.domain.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class AdminViewController {

    private final MemberService memberService;

    @GetMapping("/admin")
    public String dashboard(@AuthenticationPrincipal Long memberId, Model model) {
        model.addAttribute("adminName", memberService.getName(memberId));
        return "admin/dashboard";
    }
}
