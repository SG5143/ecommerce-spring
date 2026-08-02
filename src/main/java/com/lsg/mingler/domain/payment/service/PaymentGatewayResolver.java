package com.lsg.mingler.domain.payment.service;

import jakarta.annotation.PostConstruct;
import java.util.Locale;
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
     * 선택된 제공자 구현의 존재 여부와 토스 테스트 키 구성을 검증한다.
     */
    @PostConstruct
    void validateConfiguration() {
        String provider = currentProvider();

        if (!gateways.containsKey(provider)) {
            throw new IllegalStateException("지원하지 않는 결제 제공사입니다: " + properties.provider());
        }

        if ("TOSS".equals(provider) && !hasValidTossTestCredentials()) {
            throw new IllegalStateException(
                    "Toss 테스트 결제에는 test_ck_ 클라이언트 키와 test_sk_ 시크릿 키가 모두 필요합니다.");
        }
    }

    /**
     * 요청값을 지원되는 제공자 코드로 정규화하고 현재 설정에서 사용할 수 있는지 검증한다.
     * 요청값이 없으면 기존 {@code payment.provider} 설정을 호환 기본값으로 사용한다.
     *
     * @param requestedProvider 클라이언트가 선택한 제공자
     * @return 검증된 대문자 제공자 코드
     */
    public String resolveAvailableProvider(String requestedProvider) {
        String provider = requestedProvider == null || requestedProvider.isBlank()
                ? currentProvider()
                : requestedProvider.trim().toUpperCase(Locale.ROOT);

        if (!gateways.containsKey(provider)) {
            throw new IllegalArgumentException("지원하지 않는 결제 제공사입니다: " + requestedProvider);
        }

        if (!isAvailable(provider)) {
            throw new IllegalArgumentException("현재 사용할 수 없는 결제 제공사입니다: " + provider);
        }
        return provider;
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
        return properties.provider().toUpperCase(Locale.ROOT);
    }

    /**
     * 제공자가 현재 새 결제에 사용 가능한지 확인한다.
     *
     * @param provider 확인할 제공자 코드
     * @return 구현체가 존재하고 필요한 설정이 유효하면 {@code true}
     */
    public boolean isAvailable(String provider) {
        if (provider == null || provider.isBlank()) {
            return false;
        }
        String normalized = provider.trim().toUpperCase(Locale.ROOT);
        return gateways.containsKey(normalized)
                && (!"TOSS".equals(normalized) || hasValidTossTestCredentials());
    }

    /**
     * 지정한 제공자의 브라우저 SDK용 클라이언트 키를 반환한다.
     *
     * @param provider 결제 시도에 저장된 제공자 코드
     * @return Toss이면 클라이언트 키, 가상 결제이면 {@code null}
     */
    public String clientKey(String provider) {
        return "TOSS".equals(provider) ? properties.toss().clientKey() : null;
    }

    private boolean hasValidTossTestCredentials() {
        String clientKey = properties.toss().clientKey();
        String secretKey = properties.toss().secretKey();
        if (clientKey == null || clientKey.isBlank() || secretKey == null || secretKey.isBlank()) {
            return false;
        }

        boolean clientTest = clientKey.startsWith("test_ck_");
        boolean secretTest = secretKey.startsWith("test_sk_");

        // 라이브 결제를 도입할 때 UI 안내와 운영 안전장치를 함께 검토한 뒤 아래 검증을 활성화한다.
        // boolean clientLive = clientKey.startsWith("live_ck_");
        // boolean secretLive = secretKey.startsWith("live_sk_");
        // return (clientTest && secretTest) || (clientLive && secretLive);
        return clientTest && secretTest;
    }
}
