package com.lsg.mingler.global.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HashUtilsTest {

    @Test
    void 문자열을_SHA256_소문자_hex로_변환한다() {
        String hash = HashUtils.sha256Hex("guest-token");

        assertThat(hash)
                .isEqualTo("1da7e95ba163e2a04fb0079b15fcaebfeec45f916e108f2b799f5b8cead9e46c")
                .hasSize(64);
    }
}
