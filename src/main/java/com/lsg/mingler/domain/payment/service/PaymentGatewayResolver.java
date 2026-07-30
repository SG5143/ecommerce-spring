package com.lsg.mingler.domain.payment.service;

import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 설정된 결제 제공자 코드에 맞는 {@link PaymentGateway} 구현체를 선택하고 시작 시 키 구성을 검증한다.
 */
@Component
public class PaymentGatewayResolver {

    private final PaymentProperties properties;
    private final Map<String, PaymentGateway> gateways;

    /**
     * 등록된 게이트웨이를 제공자 코드별 맵으로 구성한다.
     *
     * @param properties 결제 제공자와 토스 키 설정
     * @param gateways Spring에 등록된 결제 게이트웨이 구현체 목록
     */
    public PaymentGatewayResolver(PaymentProperties properties, java.util.List<PaymentGateway> gateways) {
        this.properties = properties;
        this.gateways = gateways.stream().collect(Collectors.toUnmodifiableMap(
                PaymentGateway::provider,
                Function.identity()));
    }

    /**
     * 선택된 제공자 구현의 존재 여부와 토스 클라이언트·시크릿 키 종류의 일치 여부를 검증한다.
     */
    @PostConstruct
    void validateConfiguration() {
        String provider = currentProvider();

        if (!gateways.containsKey(provider)) {
            throw new IllegalStateException("지원하지 않는 결제 제공사입니다: " + properties.provider());
        }

        if (!"TOSS".equals(provider)) {
            return;
        }

        String clientKey = properties.toss().clientKey();
        String secretKey = properties.toss().secretKey();
        if (clientKey == null || clientKey.isBlank() || secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("Toss 결제에는 클라이언트 키와 시크릿 키가 모두 필요합니다.");
        }

        boolean clientTest = clientKey.startsWith("test_");
        boolean secretTest = secretKey.startsWith("test_");
        boolean clientLive = clientKey.startsWith("live_");
        boolean secretLive = secretKey.startsWith("live_");

        if ((!clientTest && !clientLive) || (!secretTest && !secretLive) || clientTest != secretTest) {
            throw new IllegalStateException("Toss 클라이언트 키와 시크릿 키의 테스트·라이브 종류가 일치해야 합니다.");
        }
    }

    /**
     * 현재 설정에서 선택된 결제 게이트웨이를 반환한다.
     *
     * @return 현재 결제 게이트웨이
     */
    public PaymentGateway current() {
        return require(currentProvider());
    }

    /**
     * 제공자 코드에 해당하는 결제 게이트웨이를 반환한다.
     *
     * @param provider 조회할 제공자 코드
     * @return 일치하는 결제 게이트웨이
     */
    public PaymentGateway require(String provider) {
        PaymentGateway gateway = gateways.get(provider);
        if (gateway == null) {
            throw new IllegalStateException("결제 제공사 구현을 찾을 수 없습니다: " + provider);
        }
        return gateway;
    }

    /**
     * 설정된 제공자 코드를 게이트웨이 식별 형식인 대문자로 정규화한다.
     *
     * @return 정규화된 현재 제공자 코드
     */
    public String currentProvider() {
        return properties.provider().toUpperCase(java.util.Locale.ROOT);
    }

    /**
     * 토스 결제창 초기화에 사용할 클라이언트 키를 반환한다.
     *
     * @return 토스 모드이면 클라이언트 키, 다른 제공자이면 {@code null}
     */
    public String clientKey() {
        return "TOSS".equals(currentProvider()) ? properties.toss().clientKey() : null;
    }
}
