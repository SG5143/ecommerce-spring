package com.lsg.mingler.global.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 자주 변경되지 않는 조회 데이터를 Caffeine 로컬 캐시에 보관하는 설정 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** 헤더 카테고리 메뉴를 10분 동안 캐시해 매 화면 요청의 반복 조회를 줄인다. */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("headerCategories");
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(1) // 매개변수 없는 헤더 메뉴 조회 결과 하나만 저장
                .expireAfterWrite(Duration.ofMinutes(10))); // DB의 카테고리 변경사항을 최대 10분 이내 다시 반영
        return cacheManager;
    }

}
