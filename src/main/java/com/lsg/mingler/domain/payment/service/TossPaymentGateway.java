package com.lsg.mingler.domain.payment.service;

import com.lsg.mingler.global.error.PaymentGatewayDeclinedException;
import com.lsg.mingler.global.error.PaymentGatewayNotFoundException;
import com.lsg.mingler.global.error.PaymentGatewayUncertainException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Set;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 토스페이먼츠 Core API를 호출해 카드 결제를 승인하고 결제 키 또는 주문번호로 상태를 조회
 * 시크릿 키 Basic 인증과 멱등성 키를 모든 승인 요청에 적용
 */
@Component
public class TossPaymentGateway implements PaymentGateway {

    private static final Set<String> UNCERTAIN_CODES = Set.of(
            "PROVIDER_ERROR",
            "IDEMPOTENT_REQUEST_PROCESSING",
            "ALREADY_PROCESSED_PAYMENT");

    private final PaymentProperties properties;
    private final ObjectMapper objectMapper;
    private final Environment environment;
    private final RestClient restClient;

    /**
     * 설정된 제한시간과 토스 API 기본 주소를 사용하는 HTTP 클라이언트를 구성한다.
     *
     * @param properties 토스 키와 HTTP 설정
     * @param objectMapper 토스 오류 응답 역직렬화 도구
     * @param environment 활성 프로필 확인용 환경 정보
     */
    public TossPaymentGateway(PaymentProperties properties, ObjectMapper objectMapper, Environment environment) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.environment = environment;

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.toss().connectTimeout())
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.toss().readTimeout());

        this.restClient = RestClient.builder()
                .baseUrl(properties.toss().baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * 토스 결제 구현체를 식별하는 제공자 코드를 반환한다.
     *
     * @return 토스 제공자 코드
     */
    @Override
    public String provider() {
        return "TOSS";
    }

    /**
     * 토스 승인 API에 결제 키, 주문번호, 금액과 멱등성 키를 전달한다.
     *
     * @param command 서버에서 검증한 결제 승인 명령
     * @return 정규화된 토스 승인 결과
     */
    @Override
    public PaymentGatewayResult confirm(PaymentGatewayCommand command) {
        try {
            TossPaymentResponse response = restClient.post()
                    .uri("/v1/payments/confirm")
                    .headers(headers -> {
                        applyCommonHeaders(headers);
                        headers.set("Idempotency-Key", command.idempotencyKey());
                        applyTestCode(headers);
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new TossConfirmRequest(command.paymentKey(), command.orderId(), command.amount()))
                    .retrieve()
                    .body(TossPaymentResponse.class);
            return toResult(response);
        } catch (RestClientResponseException e) {
            throw mapFailure(e);
        } catch (ResourceAccessException e) {
            throw new PaymentGatewayUncertainException("TOSS_COMMUNICATION_ERROR", "토스 결제 승인 결과를 확인하지 못했습니다.", e);
        }
    }

    /**
     * 토스 결제 키로 현재 결제 상태를 조회한다.
     *
     * @param paymentKey 토스가 발급한 결제 키
     * @return 정규화된 토스 결제 결과
     */
    @Override
    public PaymentGatewayResult lookupByPaymentKey(String paymentKey) {
        return lookup("/v1/payments/{paymentKey}", paymentKey);
    }

    /**
     * 토스 주문번호로 현재 결제 상태를 조회한다.
     *
     * @param orderId 결제 준비 시 토스에 전달한 주문번호
     * @return 정규화된 토스 결제 결과
     */
    @Override
    public PaymentGatewayResult lookupByOrderId(String orderId) {
        return lookup("/v1/payments/orders/{orderId}", orderId);
    }

    private PaymentGatewayResult lookup(String uri, String value) {
        try {
            TossPaymentResponse response = restClient.get()
                    .uri(uri, value)
                    .headers(this::applyCommonHeaders)
                    .retrieve()
                    .body(TossPaymentResponse.class);
            return toResult(response);
        } catch (RestClientResponseException e) {
            TossErrorResponse error = parseError(e);
            if (e.getStatusCode().value() == 404 || "NOT_FOUND_PAYMENT".equals(error.code())) {
                throw new PaymentGatewayNotFoundException(error.message());
            }
            throw new PaymentGatewayUncertainException(error.code(), error.message(), e);
        } catch (ResourceAccessException e) {
            throw new PaymentGatewayUncertainException("TOSS_LOOKUP_COMMUNICATION_ERROR", "토스 결제 조회 결과를 확인하지 못했습니다.", e);
        }
    }

    private void applyCommonHeaders(HttpHeaders headers) {
        String credential = properties.toss().secretKey() + ":";
        String encoded = Base64.getEncoder().encodeToString(credential.getBytes(StandardCharsets.UTF_8));
        headers.set(HttpHeaders.AUTHORIZATION, "Basic " + encoded);
    }

    private void applyTestCode(HttpHeaders headers) {
        String testCode = properties.toss().testCode();
        if (testCode == null || testCode.isBlank()) {
            return;
        }
        boolean local = java.util.Arrays.asList(environment.getActiveProfiles()).contains("local");
        if (local && properties.toss().secretKey().startsWith("test_")) {
            headers.set("TossPayments-Test-Code", testCode.trim());
        }
    }

    private RuntimeException mapFailure(RestClientResponseException exception) {
        TossErrorResponse error = parseError(exception);
        if (exception.getStatusCode().is5xxServerError() || UNCERTAIN_CODES.contains(error.code())) {
            return new PaymentGatewayUncertainException(error.code(), error.message(), exception);
        }
        return new PaymentGatewayDeclinedException(error.code(), error.message());
    }

    private TossErrorResponse parseError(RestClientResponseException exception) {
        try {
            TossErrorResponse error = objectMapper.readValue(
                    exception.getResponseBodyAsString(),
                    TossErrorResponse.class);
            if (error.code() != null && error.message() != null) {
                return error;
            }
        } catch (JacksonException ignored) {}

        return new TossErrorResponse("TOSS_HTTP_" + exception.getStatusCode().value(), "토스 결제 요청을 처리하지 못했습니다.");
    }

    private PaymentGatewayResult toResult(TossPaymentResponse response) {
        if (response == null) {
            throw new PaymentGatewayUncertainException("EMPTY_TOSS_RESPONSE", "토스 결제 응답이 비어 있습니다.");
        }

        String failureCode = response.failure() == null ? null : response.failure().code();
        String failureMessage = response.failure() == null ? null : response.failure().message();

        return new PaymentGatewayResult(
                response.paymentKey(),
                response.orderId(),
                response.totalAmount(),
                response.status(),
                response.method(),
                response.lastTransactionKey(),
                response.approvedAt(),
                failureCode,
                failureMessage);
    }

    private record TossConfirmRequest(String paymentKey, String orderId, Integer amount) {}

    private record TossPaymentResponse(
            String paymentKey,
            String orderId,
            Integer totalAmount,
            String status,
            String method,
            String lastTransactionKey,
            OffsetDateTime approvedAt,
            TossErrorResponse failure
    ) {}

    private record TossErrorResponse(String code, String message) {}
}
