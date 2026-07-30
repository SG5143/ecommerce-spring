package com.lsg.mingler.domain.payment.service;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentGatewayResolverTest {

    @Test
    void 유효한_토스_테스트키가_있으면_TOSS와_VIRTUAL을_모두_사용할수있다() {
        PaymentGatewayResolver resolver = resolver(
                "toss",
                "test_ck_key",
                "test_sk_key");

        resolver.validateConfiguration();

        assertThat(resolver.isAvailable("TOSS")).isTrue();
        assertThat(resolver.isAvailable("VIRTUAL")).isTrue();
        assertThat(resolver.resolveAvailableProvider(" toss ")).isEqualTo("TOSS");
        assertThat(resolver.resolveAvailableProvider("virtual")).isEqualTo("VIRTUAL");
    }

    @Test
    void 토스키가_없으면_VIRTUAL만_사용할수있다() {
        PaymentGatewayResolver resolver = resolver("virtual", "", "");

        resolver.validateConfiguration();

        assertThat(resolver.isAvailable("TOSS")).isFalse();
        assertThat(resolver.isAvailable("VIRTUAL")).isTrue();
        assertThatThrownBy(() -> resolver.resolveAvailableProvider("TOSS"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("현재 사용할 수 없는");
    }

    @Test
    void 기본제공자가_VIRTUAL이면_라이브키가_있어도_TOSS를_사용할수없다() {
        PaymentGatewayResolver resolver = resolver(
                "virtual",
                "live_ck_key",
                "live_sk_key");

        resolver.validateConfiguration();

        assertThat(resolver.isAvailable("TOSS")).isFalse();
        assertThat(resolver.isAvailable("VIRTUAL")).isTrue();
    }

    @Test
    void 기본제공자가_TOSS인데_라이브키면_설정검증에_실패한다() {
        PaymentGatewayResolver resolver = resolver(
                "toss",
                "live_ck_key",
                "live_sk_key");

        assertThatThrownBy(resolver::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Toss 테스트 결제");
    }

    @Test
    void 종류가_섞이거나_정확한_테스트키_접두사가_아니면_TOSS를_사용할수없다() {
        List<String[]> invalidCredentials = List.of(
                new String[]{"test_ck_key", "live_sk_key"},
                new String[]{"live_ck_key", "test_sk_key"},
                new String[]{"test_key", "test_sk_key"},
                new String[]{"test_ck_key", "test_key"},
                new String[]{"invalid", "invalid"});

        for (String[] credentials : invalidCredentials) {
            PaymentGatewayResolver resolver = resolver(
                    "virtual",
                    credentials[0],
                    credentials[1]);

            assertThat(resolver.isAvailable("TOSS"))
                    .as("clientKey=%s, secretKey=%s", credentials[0], credentials[1])
                    .isFalse();
        }
    }

    @Test
    void 제공자_요청값이_없으면_설정된_기본제공자를_사용한다() {
        PaymentGatewayResolver resolver = resolver("virtual", "", "");

        assertThat(resolver.resolveAvailableProvider(null)).isEqualTo("VIRTUAL");
    }

    @Test
    void 등록되지_않은_제공자는_거부한다() {
        PaymentGatewayResolver resolver = resolver("virtual", "", "");

        assertThatThrownBy(() -> resolver.resolveAvailableProvider("UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("지원하지 않는");
    }

    private PaymentGatewayResolver resolver(
            String defaultProvider,
            String clientKey,
            String secretKey) {
        PaymentProperties properties = new PaymentProperties(
                defaultProvider,
                new PaymentProperties.Toss(
                        clientKey,
                        secretKey,
                        "https://api.tosspayments.com",
                        "",
                        Duration.ofSeconds(3),
                        Duration.ofSeconds(10)),
                new PaymentProperties.Reconciliation(
                        Duration.ofSeconds(30),
                        Duration.ofSeconds(10),
                        Duration.ofMinutes(15),
                        Duration.ofMinutes(30),
                        100));
        PaymentGateway toss = mock(PaymentGateway.class);
        PaymentGateway virtual = mock(PaymentGateway.class);
        when(toss.provider()).thenReturn("TOSS");
        when(virtual.provider()).thenReturn("VIRTUAL");
        return new PaymentGatewayResolver(properties, List.of(toss, virtual));
    }
}
