package com.lsg.mingler.global.error;

/**
 * 인증 실패(아이디/비밀번호 불일치, 계정 잠금, 비정상 상태, Refresh 토큰 무효 등)를 나타내는 예외
 * GlobalExceptionHandler 가 401 UNAUTHORIZED 로 변환
 */
public class AuthenticationException extends RuntimeException {

    public AuthenticationException(String message) {
        super(message);
    }

}
