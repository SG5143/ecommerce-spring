package com.lsg.mingler.domain.cart.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GuestCartTokenManagerTest {

    private final GuestCartTokenManager tokenManager = new GuestCartTokenManager();

    @Test
    void 비회원_토큰은_매번_다른_256비트_난수로_생성된다() {
        String first = tokenManager.generate();
        String second = tokenManager.generate();

        assertThat(first).hasSize(43);
        assertThat(second).hasSize(43).isNotEqualTo(first);
    }

    @Test
    void 원문_토큰은_SHA256_해시로_변환된다() {
        String hash = tokenManager.hash("guest-token");

        assertThat(hash).isEqualTo("1da7e95ba163e2a04fb0079b15fcaebfeec45f916e108f2b799f5b8cead9e46c");
    }

    @Test
    void null과_공백_토큰은_해싱하지_않는다() {
        assertThat(tokenManager.hash(null)).isNull();
        assertThat(tokenManager.hash(" ")).isNull();
    }
}
