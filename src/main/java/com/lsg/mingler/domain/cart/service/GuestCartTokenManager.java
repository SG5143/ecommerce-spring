package com.lsg.mingler.domain.cart.service;

import com.lsg.mingler.global.util.HashUtils;
import jakarta.servlet.http.HttpServletResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class GuestCartTokenManager {

    public static final String COOKIE_NAME = "guestCartToken";
    public static final Duration VALIDITY = Duration.ofDays(30);
    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 비회원 장바구니를 식별할 256비트 난수 토큰을 생성한다.
     *
     * @return URL-safe Base64로 인코딩된 원문 토큰
     */
    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 원문 토큰을 데이터베이스 조회에 사용할 SHA-256 해시로 변환한다.
     *
     * @param rawToken 쿠키에서 전달받은 원문 토큰
     * @return SHA-256 소문자 hex 문자열, 입력이 없거나 공백이면 {@code null}
     */
    public String hash(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return null;
        return HashUtils.sha256Hex(rawToken);
    }

    /**
     * 비회원 장바구니 토큰을 HttpOnly 쿠키로 발급하거나 유효기간을 갱신한다.
     *
     * @param response 쿠키 헤더를 추가할 HTTP 응답
     * @param rawToken 쿠키에 저장할 원문 토큰
     */
    public void issueCookie(HttpServletResponse response, String rawToken) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, rawToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(VALIDITY)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /**
     * 비회원 장바구니 쿠키의 유효기간을 0으로 설정해 즉시 만료시킨다.
     *
     * @param response 만료 쿠키 헤더를 추가할 HTTP 응답
     */
    public void expireCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
