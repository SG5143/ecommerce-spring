package com.lsg.mingler.global.jwt;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseCookie;

/**
 * 일반 페이지 이동으로도 관리자 SSR 화면을 인증할 수 있도록 Access 토큰을 /admin 경로에만 제한해 관리한다.
 */
public final class AdminAccessTokenCookie {

    public static final String NAME = "adminAccessToken";
    private static final String PATH = "/admin";

    private AdminAccessTokenCookie() {
    }

    /** 관리자 페이지 전용 HttpOnly Access 토큰 쿠키를 응답에 추가한다. */
    public static void issue(HttpServletResponse response, String accessToken, Duration validity) {
        ResponseCookie cookie = ResponseCookie.from(NAME, accessToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(PATH)
                .maxAge(validity)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /** 로그아웃 시 관리자 페이지 전용 Access 토큰 쿠키를 즉시 만료시킨다. */
    public static void expire(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(NAME, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(PATH)
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /** Bearer 헤더가 없는 관리자 GET/HEAD 페이지 요청에서만 쿠키 토큰을 꺼낸다. */
    public static String resolve(HttpServletRequest request) {
        if (!isAdminPageNavigation(request)) {
            return null;
        }
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private static boolean isAdminPageNavigation(HttpServletRequest request) {
        String method = request.getMethod();
        if (!HttpMethod.GET.matches(method) && !HttpMethod.HEAD.matches(method)) {
            return false;
        }

        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String path = contextPath.isEmpty() ? requestUri : requestUri.substring(contextPath.length());
        return "/admin".equals(path) || path.startsWith("/admin/");
    }
}
