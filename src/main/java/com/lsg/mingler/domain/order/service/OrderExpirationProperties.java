package com.lsg.mingler.domain.order.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 결제 대기 주문의 자동 만료 정책 */
@ConfigurationProperties(prefix = "order.expiration")
public record OrderExpirationProperties(Duration ttl, Duration fixedDelay, int batchSize) {

    public OrderExpirationProperties {
        ttl = ttl == null ? Duration.ofMinutes(30) : ttl;
        fixedDelay = fixedDelay == null ? Duration.ofMinutes(1) : fixedDelay;
        batchSize = batchSize < 1 ? 100 : batchSize;
    }
}
