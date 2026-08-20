package com.lsg.mingler.global.config;

import com.lsg.mingler.domain.product.service.CategoryService;
import com.lsg.mingler.global.jwt.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminAuthorizationTest.AdminProbeController.class)
@Import({SecurityConfig.class, AdminAuthorizationTest.AdminProbeController.class})
class AdminAuthorizationTest {

    private static final String ADMIN_PAGE_PATH = "/admin/test";
    private static final String ADMIN_API_PATH = "/api/v1/admin/test";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private CategoryService categoryService;

    @Test
    void 미인증_사용자는_관리자_페이지와_API에_접근할_수_없다() throws Exception {
        assertUnauthorizedWithoutToken(ADMIN_PAGE_PATH);
        assertUnauthorizedWithoutToken(ADMIN_API_PATH);
    }

    @Test
    void 유효하지_않은_토큰으로_관리자_경로에_접근하면_401을_반환한다() throws Exception {
        when(jwtTokenProvider.validate("invalid-token")).thenReturn(false);

        mockMvc.perform(get(ADMIN_API_PATH).header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("유효하지 않거나 만료된 인증 정보입니다."));
    }

    @Test
    void 일반_회원은_관리자_페이지와_API에_접근할_수_없다() throws Exception {
        mockValidToken("user-token", "USER");

        assertForbiddenWithToken(ADMIN_PAGE_PATH, "user-token");
        assertForbiddenWithToken(ADMIN_API_PATH, "user-token");
    }

    @Test
    void 관리자는_관리자_페이지와_API에_접근할_수_있다() throws Exception {
        mockValidToken("admin-token", "ADMIN");

        assertAllowedWithToken(ADMIN_PAGE_PATH, "admin-token");
        assertAllowedWithToken(ADMIN_API_PATH, "admin-token");
    }

    private void assertUnauthorizedWithoutToken(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."));
    }

    private void assertForbiddenWithToken(String path, String token) throws Exception {
        mockMvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("접근 권한이 없습니다."));
    }

    private void assertAllowedWithToken(String path, String token) throws Exception {
        mockMvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    private void mockValidToken(String token, String role) {
        when(jwtTokenProvider.validate(token)).thenReturn(true);
        when(jwtTokenProvider.getMemberId(token)).thenReturn(1L);
        when(jwtTokenProvider.getRole(token)).thenReturn(role);
    }

    @RestController
    public static class AdminProbeController {

        @GetMapping({ADMIN_PAGE_PATH, ADMIN_API_PATH})
        String probe() {
            return "ok";
        }
    }
}
