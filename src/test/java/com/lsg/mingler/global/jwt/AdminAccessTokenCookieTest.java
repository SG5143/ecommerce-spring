package com.lsg.mingler.global.jwt;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class AdminAccessTokenCookieTest {

    @Test
    void 관리자_GET_페이지_요청이면_쿠키의_Access_토큰을_반환한다() {
        MockHttpServletRequest request = adminRequest("GET", "/admin/products");

        String token = AdminAccessTokenCookie.resolve(request);

        assertThat(token).isEqualTo("access-token");
    }

    @Test
    void 관리자_HEAD_페이지_요청이면_쿠키의_Access_토큰을_반환한다() {
        MockHttpServletRequest request = adminRequest("HEAD", "/admin");

        String token = AdminAccessTokenCookie.resolve(request);

        assertThat(token).isEqualTo("access-token");
    }

    @Test
    void 관리자_API나_상태변경_요청에는_쿠키_토큰을_사용하지_않는다() {
        MockHttpServletRequest apiRequest = adminRequest("GET", "/api/v1/admin/products");
        MockHttpServletRequest postRequest = adminRequest("POST", "/admin/products");

        assertThat(AdminAccessTokenCookie.resolve(apiRequest)).isNull();
        assertThat(AdminAccessTokenCookie.resolve(postRequest)).isNull();
    }

    private MockHttpServletRequest adminRequest(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setCookies(new Cookie(AdminAccessTokenCookie.NAME, "access-token"));
        return request;
    }
}
