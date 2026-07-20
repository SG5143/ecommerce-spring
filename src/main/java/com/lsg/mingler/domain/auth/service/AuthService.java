package com.lsg.mingler.domain.auth.service;

import com.lsg.mingler.domain.auth.dao.RefreshTokenRepository;
import com.lsg.mingler.domain.auth.dto.LoginRequest;
import com.lsg.mingler.domain.auth.dto.LoginResponse;
import com.lsg.mingler.domain.auth.dto.ReissueResponse;
import com.lsg.mingler.domain.auth.entity.RefreshToken;
import com.lsg.mingler.domain.member.dao.MemberRepository;
import com.lsg.mingler.domain.member.entity.Member;
import com.lsg.mingler.global.error.AuthenticationException;
import com.lsg.mingler.global.jwt.JwtProperties;
import com.lsg.mingler.global.jwt.JwtTokenProvider;
import com.lsg.mingler.global.util.HashUtils;
import jakarta.servlet.http.HttpServletResponse;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 로그인·토큰 재발급·로그아웃을 처리
 * - 비밀번호 실패를 회원별로 누적하고, 임계치 초과 시 계정을 잠금처리
 * - 토큰 탈취: Access 는 짧게, Refresh 는 회전(1회용)하며 폐기된 토큰 재사용 시 전체 폐기
 * <p>
 * 주의) 로그인 실패 카운트는 예외를 던져도 유지되어야 하므로 메서드 전체를 하나의 트랜잭션으로 묶지 않고,
 * 상태 변경마다 repository.save() 로 즉시 커밋
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int MAX_FAIL_COUNT = 5;
    private static final long LOCK_MINUTES = 10;
    private static final String INVALID_CREDENTIALS_MESSAGE = "아이디 또는 비밀번호가 올바르지 않습니다.";
    private static final int REFRESH_TOKEN_BYTES = 32; // 256bit

    private static final String REFRESH_COOKIE_NAME = "refreshToken";
    private static final String REFRESH_COOKIE_PATH = "/api/v1/auth";

    private final MemberRepository memberRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final JwtProperties jwtProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 로그인: 자격증명을 검증하고 Access 토큰(바디)과 Refresh 토큰(HttpOnly 쿠키)을 발급
     * 계정 존재 여부가 노출되지 않도록 아이디 미존재와 비밀번호 불일치는 동일 메시지로 응답
     */
    public LoginResponse login(LoginRequest request, HttpServletResponse response) {
        Member member = memberRepository.findByUsername(request.memberId())
                .orElseThrow(() -> new AuthenticationException(INVALID_CREDENTIALS_MESSAGE));

        if (member.isLocked()) {
            throw new AuthenticationException("로그인 실패가 반복되어 계정이 잠겼습니다. 잠시 후 다시 시도해주세요.");
        }
        if (!member.isActive()) {
            throw new AuthenticationException("로그인할 수 없는 계정 상태입니다.");
        }

        if (!passwordEncoder.matches(request.password(), member.getPassword())) {
            member.recordLoginFailure(MAX_FAIL_COUNT, LOCK_MINUTES);
            memberRepository.save(member);
            throw new AuthenticationException(INVALID_CREDENTIALS_MESSAGE);
        }

        member.resetLoginFailure();
        member.updateLastLoginAt();
        memberRepository.save(member);

        issueRefreshToken(member.getId(), response);
        String accessToken = tokenProvider.createAccessToken(member.getId(), member.getRole());
        return new LoginResponse(accessToken);
    }

    /**
     * 토큰 재발급: 쿠키의 Refresh 토큰을 검증하고 재발급
     * 이미 폐기된 토큰이 다시 들어오면 탈취로 간주해 해당 회원의 모든 Refresh 토큰을 폐기
     */
    public ReissueResponse reissue(String refreshTokenValue, HttpServletResponse response) {
        if (refreshTokenValue == null || refreshTokenValue.isBlank()) {
            throw new AuthenticationException("인증 정보가 없습니다. 다시 로그인해주세요.");
        }

        RefreshToken stored = refreshTokenRepository.findByTokenHash(HashUtils.sha256Hex(refreshTokenValue))
                .orElseThrow(() -> new AuthenticationException("유효하지 않은 인증 정보입니다. 다시 로그인해주세요."));

        if (stored.isRevoked()) {
            revokeAllTokens(stored.getMemberId());
            throw new AuthenticationException("비정상적인 접근이 감지되어 로그아웃 되었습니다. 다시 로그인해주세요.");
        }
        if (stored.isExpired()) {
            throw new AuthenticationException("인증이 만료되었습니다. 다시 로그인해주세요.");
        }

        Member member = memberRepository.findById(stored.getMemberId())
                .filter(Member::isActive)
                .orElseThrow(() -> new AuthenticationException("로그인할 수 없는 계정 상태입니다."));

        stored.revoke();
        refreshTokenRepository.save(stored);

        issueRefreshToken(member.getId(), response);
        String accessToken = tokenProvider.createAccessToken(member.getId(), member.getRole());
        return new ReissueResponse(accessToken);
    }

    /**
     * 로그아웃: 전달된 Refresh 토큰을 폐기하고 쿠키를 삭제
     */
    public void logout(String refreshTokenValue, HttpServletResponse response) {
        if (refreshTokenValue != null && !refreshTokenValue.isBlank()) {
            refreshTokenRepository.findByTokenHash(HashUtils.sha256Hex(refreshTokenValue))
                    .ifPresent(token -> {
                        token.revoke();
                        refreshTokenRepository.save(token);
                    });
        }
        expireRefreshCookie(response);
    }

    /**
     * 새 Refresh 토큰을 생성해 해시로 저장하고, 원문을 HttpOnly 쿠키로 응답에 싣기
     */
    private void issueRefreshToken(Long memberId, HttpServletResponse response) {
        String rawToken = generateRawToken();
        Duration validity = jwtProperties.refreshTokenValidity();

        RefreshToken refreshToken = RefreshToken.builder()
                .memberId(memberId)
                .tokenHash(HashUtils.sha256Hex(rawToken))
                .expiresAt(LocalDateTime.now().plus(validity))
                .build();
        refreshTokenRepository.save(refreshToken);

        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, rawToken)
                .httpOnly(true)      // JS 접근 차단
                .secure(true)        // HTTPS 에서만 전송
                .sameSite("Strict")  // 크로스 사이트 요청에 쿠키 미전송
                .path(REFRESH_COOKIE_PATH)
                .maxAge(validity)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /**
     * Refresh 쿠키를 즉시 만료시켜 클라이언트에서 삭제
     */
    private void expireRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /**
     * 해당 회원의 Refresh 토큰을 모두 폐기한다 (탈취 탐지·비밀번호 변경 시 전체 로그아웃).
     */
    public void revokeAllTokens(Long memberId) {
        List<RefreshToken> tokens = refreshTokenRepository.findAllByMemberId(memberId);
        tokens.forEach(RefreshToken::revoke);
        refreshTokenRepository.saveAll(tokens);
    }

    /**
     * 256bit SecureRandom 난수를 URL-safe Base64 문자열로 변환 (Refresh 토큰 원문)
     */
    private String generateRawToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

}
