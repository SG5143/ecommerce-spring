package com.lsg.mingler.domain.payment.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.lsg.mingler.domain.payment.dto.PaymentConfirmResponse;
import com.lsg.mingler.global.error.DuplicateException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class PaymentRequestCoordinator {

    private static final long MAXIMUM_REQUESTS = 10_000;
    private static final Duration SUCCESS_RETENTION = Duration.ofMinutes(10);

    private final Cache<String, CoordinatedRequest> requests = Caffeine.newBuilder()
            .maximumSize(MAXIMUM_REQUESTS)
            .expireAfterWrite(SUCCESS_RETENTION)
            .build();

    /**
     * 동일한 소유자와 멱등성 키로 들어온 결제 요청을 하나의 실행으로 합친다.
     * 같은 요청은 진행 중인 결과를 공유하고, 요청 내용이 다르면 중복 요청으로 거부한다.
     * 실패한 실행은 캐시에서 제거해 정상적인 재시도를 허용한다.
     *
     * @param cacheKey 소유자 범위가 포함된 멱등성 캐시 키
     * @param fingerprint 주문번호, 금액, 결제수단으로 만든 요청 식별값
     * @param action 최초 요청이 수행할 결제 승인 작업
     * @return 최초 실행 또는 공유된 실행의 결제 승인 결과
     * @throws DuplicateException 같은 캐시 키에 서로 다른 요청 내용이 사용된 경우
     */
    public PaymentConfirmResponse coordinate(String cacheKey, String fingerprint, Supplier<PaymentConfirmResponse> action) {
        AtomicBoolean owner = new AtomicBoolean(false);
        CoordinatedRequest coordinated = requests.asMap().compute(cacheKey, (key, existing) -> {
            if (existing == null) {
                owner.set(true);
                return new CoordinatedRequest(fingerprint, new CompletableFuture<>());
            }
            return existing;
        });

        if (!coordinated.fingerprint().equals(fingerprint)) {
            throw new DuplicateException("결제 요청 정보가 이전 요청과 달라 처리할 수 없습니다. 결제 내용을 확인한 후 다시 시도해주세요.");
        }

        if (owner.get()) {
            try {
                PaymentConfirmResponse response = action.get();
                coordinated.future().complete(response);
                return response;
            } catch (RuntimeException | Error e) {
                coordinated.future().completeExceptionally(e);
                requests.asMap().remove(cacheKey, coordinated);
                throw e;
            }
        }

        try {
            return coordinated.future().join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw e;
        }
    }

    private record CoordinatedRequest(
            String fingerprint,
            CompletableFuture<PaymentConfirmResponse> future
    ) {
    }
}
