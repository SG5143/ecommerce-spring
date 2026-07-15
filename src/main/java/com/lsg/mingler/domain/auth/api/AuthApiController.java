package com.lsg.mingler.domain.auth.api;

import com.lsg.mingler.domain.auth.dto.LoginRequest;
import com.lsg.mingler.domain.auth.dto.LoginResponse;
import com.lsg.mingler.domain.auth.dto.ReissueResponse;
import com.lsg.mingler.domain.auth.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthApiController {

    private static final String REFRESH_COOKIE_NAME = "refreshToken";

    private final AuthService authService;

    /**
     * 로그인. Access 토큰은 바디로, Refresh 토큰은 HttpOnly 쿠키로 응답
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request, HttpServletResponse response) {
        return ResponseEntity.ok(authService.login(request, response));
    }

    /**
     * Refresh 쿠키로 Access 토큰을 재발급하고 Refresh 토큰을 재발급
     */
    @PostMapping("/reissue")
    public ResponseEntity<ReissueResponse> reissue(@CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken, HttpServletResponse response) {
        return ResponseEntity.ok(authService.reissue(refreshToken, response));
    }

    /**
     * 로그아웃. Refresh 토큰을 폐기하고 쿠키 삭제
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken, HttpServletResponse response) {
        authService.logout(refreshToken, response);
        return ResponseEntity.ok().build();
    }

}
