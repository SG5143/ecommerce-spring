package com.lsg.mingler.domain.payment.service;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import com.lsg.mingler.global.error.PaymentGatewayDeclinedException;
import com.lsg.mingler.global.error.PaymentGatewayUncertainException;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 로컬 HTTP 서버를 사용해 토스 승인 요청의 인증·본문·멱등성 헤더와 HTTP 오류 분류를 검증한다.
 */
class TossPaymentGatewayTest {

    private HttpServer server;
    private AtomicReference<String> authorization;
    private AtomicReference<String> idempotencyKey;
    private AtomicReference<String> testCode;
    private AtomicReference<String> requestBody;
    private AtomicInteger responseStatus;
    private AtomicReference<String> responseBody;

    @BeforeEach
    void setUp() throws IOException {
        authorization = new AtomicReference<>();
        idempotencyKey = new AtomicReference<>();
        testCode = new AtomicReference<>();
        requestBody = new AtomicReference<>();
        responseStatus = new AtomicInteger(200);
        responseBody = new AtomicReference<>("""
                {
                  "paymentKey": "payment-key",
                  "orderId": "TOSS-order",
                  "totalAmount": 20000,
                  "status": "DONE",
                  "method": "카드",
                  "lastTransactionKey": "transaction-key",
                  "approvedAt": null,
                  "failure": null
                }
                """);
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/payments/confirm", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            idempotencyKey.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            testCode.set(exchange.getRequestHeaders().getFirst("TossPayments-Test-Code"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = responseBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus.get(), response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void 승인요청은_Basic인증_JSON_멱등키와_로컬_테스트코드를_전달한다() {
        TossPaymentGateway gateway = gateway();

        PaymentGatewayResult result = gateway.confirm(new PaymentGatewayCommand(
                "payment-key",
                "TOSS-order",
                20_000,
                "key-1",
                VirtualPaymentScenario.SUCCESS));

        String expectedCredential = Base64.getEncoder().encodeToString(
                "test_sk_secret:".getBytes(StandardCharsets.UTF_8));
        assertThat(authorization.get()).isEqualTo("Basic " + expectedCredential);
        assertThat(idempotencyKey.get()).isEqualTo("key-1");
        assertThat(testCode.get()).isEqualTo("PROVIDER_ERROR");
        assertThat(requestBody.get()).contains(
                "\"paymentKey\":\"payment-key\"",
                "\"orderId\":\"TOSS-order\"",
                "\"amount\":20000");
        assertThat(result.status()).isEqualTo("DONE");
    }

    @Test
    void 토스_5xx는_승인결과_불명으로_분류한다() {
        responseStatus.set(500);
        responseBody.set("""
                {"code":"FAILED_INTERNAL_SYSTEM_PROCESSING","message":"내부 오류"}
                """);

        assertThatThrownBy(() -> gateway().confirm(command()))
                .isInstanceOf(PaymentGatewayUncertainException.class);
    }

    @Test
    void 카드승인_거절은_확정실패로_분류한다() {
        responseStatus.set(403);
        responseBody.set("""
                {"code":"REJECT_CARD_PAYMENT","message":"한도 초과"}
                """);

        assertThatThrownBy(() -> gateway().confirm(command()))
                .isInstanceOf(PaymentGatewayDeclinedException.class)
                .hasMessage("한도 초과");
    }

    private TossPaymentGateway gateway() {
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"local"});
        PaymentProperties properties = new PaymentProperties(
                "toss",
                new PaymentProperties.Toss(
                        "test_ck_client",
                        "test_sk_secret",
                        "http://localhost:" + server.getAddress().getPort(),
                        "PROVIDER_ERROR",
                        Duration.ofSeconds(3),
                        Duration.ofSeconds(10)),
                new PaymentProperties.Reconciliation(
                        Duration.ofSeconds(30),
                        Duration.ofSeconds(10),
                        Duration.ofMinutes(15),
                        Duration.ofMinutes(30),
                        100));
        return new TossPaymentGateway(properties, new ObjectMapper(), environment);
    }

    private PaymentGatewayCommand command() {
        return new PaymentGatewayCommand(
                "payment-key",
                "TOSS-order",
                20_000,
                "key-1",
                VirtualPaymentScenario.SUCCESS);
    }
}
