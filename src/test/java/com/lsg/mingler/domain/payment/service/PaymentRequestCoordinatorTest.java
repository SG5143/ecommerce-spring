package com.lsg.mingler.domain.payment.service;

import com.lsg.mingler.domain.order.entity.OrderStatus;
import com.lsg.mingler.domain.payment.dto.PaymentConfirmResponse;
import com.lsg.mingler.domain.payment.entity.PaymentStatus;
import com.lsg.mingler.global.error.DuplicateException;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

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
    void 완료된_동일요청은_작업을_재실행하지_않고_성공결과를_재사용한다() {
        AtomicInteger executions = new AtomicInteger();
        PaymentConfirmResponse expected = response();

        PaymentConfirmResponse first = coordinator.coordinate("owner:key", "fingerprint", () -> {
            executions.incrementAndGet();
            return expected;
        });
        PaymentConfirmResponse replayed = coordinator.coordinate("owner:key", "fingerprint", () -> {
            executions.incrementAndGet();
            return response();
        });

        assertThat(first).isSameAs(expected);
        assertThat(replayed).isSameAs(expected);
        assertThat(executions).hasValue(1);
    }

    @Test
    void 서로_다른_회원범위는_같은_멱등성키를_독립적으로_처리한다() {
        AtomicInteger executions = new AtomicInteger();

        coordinator.coordinate("MEMBER:7:key-1", "fingerprint", () -> {
            executions.incrementAndGet();
            return response();
        });
        coordinator.coordinate("MEMBER:8:key-1", "fingerprint", () -> {
            executions.incrementAndGet();
            return response();
        });

        assertThat(executions).hasValue(2);
    }

    @Test
    void 비회원은_주문범위가_다르면_같은_멱등성키를_독립적으로_처리한다() {
        AtomicInteger executions = new AtomicInteger();

        coordinator.coordinate("GUEST:token-hash:ORD-1:key-1", "fingerprint", () -> {
            executions.incrementAndGet();
            return response();
        });
        coordinator.coordinate("GUEST:token-hash:ORD-2:key-1", "fingerprint", () -> {
            executions.incrementAndGet();
            return response();
        });

        assertThat(executions).hasValue(2);
    }

    @Test
    void 최초작업이_실패하면_대기요청도_같은예외를_받고_새로_시도할수_있다() throws Exception {
        AtomicInteger executions = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        IllegalStateException failure = new IllegalStateException("일시 오류");
        AtomicReference<Throwable> waitingFailure = new AtomicReference<>();

        CompletableFuture<PaymentConfirmResponse> first = CompletableFuture.supplyAsync(() ->
                coordinator.coordinate("owner:key", "fingerprint", () -> {
                    executions.incrementAndGet();
                    started.countDown();
                    await(release);
                    throw failure;
                }));
        assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();

        Thread waitingRequest = Thread.ofPlatform().start(() -> {
            try {
                coordinator.coordinate("owner:key", "fingerprint", () -> {
                    executions.incrementAndGet();
                    return response();
                });
            } catch (Throwable throwable) {
                waitingFailure.set(throwable);
            }
        });
        try {
            awaitWaiting(waitingRequest);
        } finally {
            release.countDown();
        }

        Throwable firstFailure = catchThrowable(first::join);
        waitingRequest.join(1_000);

        PaymentConfirmResponse retried = coordinator.coordinate(
                "owner:key", "fingerprint", () -> {
                    executions.incrementAndGet();
                    return response();
                });

        assertThat(firstFailure).isInstanceOf(CompletionException.class);
        assertThat(firstFailure.getCause()).isSameAs(failure);
        assertThat(waitingRequest.isAlive()).isFalse();
        assertThat(waitingFailure.get()).isSameAs(failure);
        assertThat(retried).isEqualTo(response());
        assertThat(executions).hasValue(2);
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

    private void awaitWaiting(Thread thread) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (System.nanoTime() < deadline) {
            Thread.State state = thread.getState();
            if (state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING) {
                return;
            }
            Thread.onSpinWait();
        }
        throw new AssertionError("동시 결제 요청이 기존 실행 결과를 기다리지 않았습니다.");
    }
}
