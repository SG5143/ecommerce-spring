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

        assertThat(hash).hasSize(64).doesNotContain("guest-token");
    }
}
