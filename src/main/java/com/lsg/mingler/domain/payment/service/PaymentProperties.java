package com.lsg.mingler.domain.payment.service;

import java.time.Duration;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code payment.*} 설정을 결제 제공자, 토스 HTTP 통신, 자동 재조정 정책으로 바인딩
 *
 * @param provider 사용할 결제 제공자
 * @param toss 토스 API 키와 HTTP 설정
 * @param reconciliation 결제 재조정 정책
 */
@ConfigurationProperties(prefix = "payment")
public record PaymentProperties(String provider, Toss toss, Reconciliation reconciliation) {

    public PaymentProperties {
        provider = provider == null ? "virtual" : provider.trim().toLowerCase(Locale.ROOT);

        toss = toss == null
                ? new Toss("", "", "https://api.tosspayments.com", "", Duration.ofSeconds(3), Duration.ofSeconds(10))
                : toss;

        reconciliation = reconciliation == null
                ? new Reconciliation(Duration.ofSeconds(30), Duration.ofSeconds(10),
                Duration.ofMinutes(15), Duration.ofMinutes(30), 100)
                : reconciliation;
    }

    /**
     * 토스페이먼츠 API 인증과 HTTP 통신 설정
     *
     * @param clientKey 브라우저 결제 SDK용 클라이언트 키
     * @param secretKey 서버 API 인증용 시크릿 키
     * @param baseUrl 토스 Core API 기본 주소
     * @param testCode 로컬 테스트 오류 재현 코드
     * @param connectTimeout HTTP 연결 제한시간
     * @param readTimeout HTTP 응답 제한시간
     */
    public record Toss(
            String clientKey,
            String secretKey,
            String baseUrl,
            String testCode,
            Duration connectTimeout,
            Duration readTimeout
    ) {
    }

    /**
     * 결과가 불명확한 결제를 자동 조회하고 만료 처리하기 위한 정책
     *
     * @param fixedDelay 스케줄러 실행 간격
     * @param processingAge 재조정 대상으로 볼 최소 PROCESSING 경과시간
     * @param expiryGrace 조회 결과가 없을 때 실패로 확정하기까지의 유예시간
     * @param preparationTtl PENDING 결제 준비의 유효시간
     * @param batchSize 한 번에 조회할 최대 결제 건수
     */
    public record Reconciliation(
            Duration fixedDelay,
            Duration processingAge,
            Duration expiryGrace,
            Duration preparationTtl,
            int batchSize
    ) {
    }
}
