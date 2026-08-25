package com.lsg.mingler.web;

import com.lsg.mingler.domain.member.service.MemberService;
import com.lsg.mingler.domain.product.service.CategoryService;
import com.lsg.mingler.global.config.SecurityConfig;
import com.lsg.mingler.global.jwt.AdminAccessTokenCookie;
import com.lsg.mingler.global.jwt.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(controllers = AdminViewController.class)
@Import(SecurityConfig.class)
class AdminViewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private CategoryService categoryService;

    @MockitoBean
    private MemberService memberService;

    @Test
    void 관리자_쿠키로_대시보드_레이아웃을_조회할_수_있다() throws Exception {
        when(jwtTokenProvider.validate("admin-token")).thenReturn(true);
        when(jwtTokenProvider.getMemberId("admin-token")).thenReturn(1L);
        when(jwtTokenProvider.getRole("admin-token")).thenReturn("ADMIN");
        when(memberService.getName(1L)).thenReturn("관리자");

        mockMvc.perform(get("/admin")
                        .cookie(new Cookie(AdminAccessTokenCookie.NAME, "admin-token")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/dashboard"))
                .andExpect(content().string(containsString("관리자 대시보드")))
                .andExpect(content().string(containsString("상품관리")))
                .andExpect(content().string(containsString("주문관리")))
                .andExpect(content().string(containsString("감사로그")))
                .andExpect(content().string(containsString("class=\"admin-header\"")))
                .andExpect(content().string(containsString("aria-label=\"관리자 메인 메뉴\"")))
                .andExpect(content().string(containsString("Mingler")))
                .andExpect(content().string(containsString("관리자님")))
                .andExpect(content().string(containsString("id=\"admin-logout\"")))
                .andExpect(content().string(containsString("로그아웃")))
                .andExpect(content().string(not(containsString("admin-session-badge"))))
                .andExpect(content().string(not(containsString("admin-store-link"))))
                .andExpect(content().string(not(containsString("class=\"site-header\""))))
                .andExpect(content().string(not(containsString("class=\"site-footer\""))));
    }
}
