package com.lsg.mingler.global.jwt;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml 의 jwt.* 설정을 바인딩하는 프로퍼티
 * secret 은 HS256 서명용 32바이트 이상 문자열, validity 는 각 토큰 수명
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String secret,
        Duration accessTokenValidity,
        Duration refreshTokenValidity
) {
}
