package com.lsg.mingler.global.jwt;

import java.time.Duration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private static final String SECRET = "mingler-test-secret-key-for-hs256-signing-256bit!";

    @Test
    void 생성한_토큰은_검증되고_회원ID와_권한을_담는다() {
        JwtTokenProvider provider = new JwtTokenProvider(
                new JwtProperties(SECRET, Duration.ofMinutes(30), Duration.ofDays(14)));

        String token = provider.createAccessToken(42L, "USER");

        assertThat(provider.validate(token)).isTrue();
        assertThat(provider.getMemberId(token)).isEqualTo(42L);
        assertThat(provider.getRole(token)).isEqualTo("USER");
    }

    @Test
    void 만료된_토큰은_검증에_실패한다() {
        JwtTokenProvider provider = new JwtTokenProvider(
                new JwtProperties(SECRET, Duration.ofMillis(-1000), Duration.ofDays(14)));

        String expired = provider.createAccessToken(1L, "USER");

        assertThat(provider.validate(expired)).isFalse();
    }

    @Test
    void 다른_키로_서명된_토큰은_검증에_실패한다() {
        JwtTokenProvider issuer = new JwtTokenProvider(
                new JwtProperties(SECRET, Duration.ofMinutes(30), Duration.ofDays(14)));
        JwtTokenProvider verifier = new JwtTokenProvider(
                new JwtProperties("another-different-secret-key-256bit-value-here!", Duration.ofMinutes(30), Duration.ofDays(14)));

        String token = issuer.createAccessToken(1L, "USER");

        assertThat(verifier.validate(token)).isFalse();
    }

}
