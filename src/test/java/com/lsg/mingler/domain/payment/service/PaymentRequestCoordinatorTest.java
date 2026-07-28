package com.lsg.mingler.domain.payment.service;

import com.lsg.mingler.domain.order.entity.OrderStatus;
import com.lsg.mingler.domain.payment.dto.PaymentConfirmResponse;
import com.lsg.mingler.domain.payment.entity.PaymentStatus;
import com.lsg.mingler.global.error.DuplicateException;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentRequestCoordinatorTest {

    private final PaymentRequestCoordinator coordinator = new PaymentRequestCoordinator();

    @Test
    void 동일한_멱등요청은_실제_작업을_한번만_실행하고_결과를_공유한다() throws Exception {
        AtomicInteger executions = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        PaymentConfirmResponse expected = response();

        CompletableFuture<PaymentConfirmResponse> first = CompletableFuture.supplyAsync(() ->
                coordinator.coordinate("owner:key", "fingerprint", () -> {
                    executions.incrementAndGet();
                    started.countDown();
                    await(release);
                    return expected;
                }));
        assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<PaymentConfirmResponse> second = CompletableFuture.supplyAsync(() ->
                coordinator.coordinate("owner:key", "fingerprint", () -> {
                    executions.incrementAndGet();
                    return expected;
                }));
        release.countDown();

        assertThat(first.get(1, TimeUnit.SECONDS)).isSameAs(expected);
        assertThat(second.get(1, TimeUnit.SECONDS)).isSameAs(expected);
        assertThat(executions).hasValue(1);
    }

    @Test
    void 동일한_키에_다른_요청이면_거부한다() {
        coordinator.coordinate("owner:key", "first", this::response);

        assertThatThrownBy(() -> coordinator.coordinate("owner:key", "second", this::response))
                .isInstanceOf(DuplicateException.class)
                .hasMessageContaining("이전 요청과 달라");
    }

    @Test
    void 작업이_실패하면_키를_제거해_재시도할_수_있다() {
        assertThatThrownBy(() -> coordinator.coordinate("owner:key", "fingerprint", () -> {
            throw new IllegalStateException("일시 오류");
        })).isInstanceOf(IllegalStateException.class);

        PaymentConfirmResponse retried = coordinator.coordinate(
                "owner:key", "fingerprint", this::response);

        assertThat(retried).isEqualTo(response());
    }

    private PaymentConfirmResponse response() {
        return new PaymentConfirmResponse(
                "PAY-1",
                "ORD-1",
                PaymentStatus.SUCCESS,
                OrderStatus.PAID,
                10_000,
                "CARD",
                "VIRTUAL",
                "VPG-1",
                LocalDateTime.of(2026, 7, 27, 12, 0));
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("테스트 대기 중 인터럽트가 발생했습니다.", e);
        }
    }
}
