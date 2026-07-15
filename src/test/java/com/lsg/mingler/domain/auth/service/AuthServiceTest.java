package com.lsg.mingler.domain.auth.service;

import com.lsg.mingler.domain.auth.dao.RefreshTokenRepository;
import com.lsg.mingler.domain.auth.dto.LoginRequest;
import com.lsg.mingler.domain.auth.dto.LoginResponse;
import com.lsg.mingler.domain.auth.entity.RefreshToken;
import com.lsg.mingler.domain.member.dao.MemberRepository;
import com.lsg.mingler.domain.member.entity.Member;
import com.lsg.mingler.global.error.AuthenticationException;
import com.lsg.mingler.global.jwt.JwtProperties;
import com.lsg.mingler.global.jwt.JwtTokenProvider;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private MemberRepository memberRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider tokenProvider;
    @Mock
    private JwtProperties jwtProperties;

    @InjectMocks
    private AuthService authService;

    private final HttpServletResponse response = mock(HttpServletResponse.class);

    private Member activeMember() {
        return Member.builder()
                .username("tester")
                .password("encoded-password")
                .name("홍길동")
                .phone("01012345678")
                .birthDate(LocalDate.of(1990, 1, 1))
                .marketingAgreed(false)
                .termsAgreedAt(LocalDateTime.now())
                .privacyAgreedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void 올바른_자격증명이면_토큰을_발급한다() {
        Member member = activeMember();
        when(memberRepository.findByUsername("tester")).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("rawpw", "encoded-password")).thenReturn(true);
        when(jwtProperties.refreshTokenValidity()).thenReturn(Duration.ofDays(14));
        when(tokenProvider.createAccessToken(any(), anyString())).thenReturn("access-token");

        LoginResponse result = authService.login(new LoginRequest("tester", "rawpw"), response);

        assertThat(result.accessToken()).isEqualTo("access-token");
        verify(refreshTokenRepository).save(any(RefreshToken.class));
        verify(memberRepository).save(member);
    }

    @Test
    void 비밀번호_5회_틀리면_계정이_잠긴다() {
        Member member = activeMember();
        when(memberRepository.findByUsername("tester")).thenReturn(Optional.of(member));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> authService.login(new LoginRequest("tester", "wrong"), response))
                    .isInstanceOf(AuthenticationException.class);
        }

        assertThat(member.getLoginFailCount()).isEqualTo(5);
        assertThat(member.isLocked()).isTrue();
    }

    @Test
    void 잠긴_계정은_로그인할_수_없다() {
        Member member = activeMember();
        member.recordLoginFailure(1, 10); // 임계치 1 로 즉시 잠금
        when(memberRepository.findByUsername("tester")).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> authService.login(new LoginRequest("tester", "rawpw"), response))
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining("잠겼");
    }

    @Test
    void 없는_아이디와_틀린_비번은_같은_메시지를_반환한다() {
        when(memberRepository.findByUsername("nobody")).thenReturn(Optional.empty());
        Throwable noAccount = org.assertj.core.api.Assertions.catchThrowable(
                () -> authService.login(new LoginRequest("nobody", "rawpw"), response));

        Member member = activeMember();
        when(memberRepository.findByUsername("tester")).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("wrong", "encoded-password")).thenReturn(false);
        Throwable wrongPassword = org.assertj.core.api.Assertions.catchThrowable(
                () -> authService.login(new LoginRequest("tester", "wrong"), response));

        assertThat(noAccount).isInstanceOf(AuthenticationException.class);
        assertThat(wrongPassword).isInstanceOf(AuthenticationException.class);
        assertThat(noAccount.getMessage()).isEqualTo(wrongPassword.getMessage());
    }

    @Test
    void 폐기된_refresh_토큰_재사용시_전체_토큰이_폐기된다() {
        RefreshToken revoked = RefreshToken.builder()
                .memberId(1L)
                .tokenHash("hash")
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();
        revoked.revoke();
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(revoked));
        when(refreshTokenRepository.findAllByMemberId(1L)).thenReturn(List.of(revoked));

        assertThatThrownBy(() -> authService.reissue("stolen-token", response))
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining("비정상적인 접근");

        verify(refreshTokenRepository).saveAll(any());
    }

}
