package com.lsg.mingler.domain.payment.entity;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentStatusTest {

    private static final Map<PaymentStatus, Set<PaymentStatus>> ALLOWED_TRANSITIONS = Map.of(
            PaymentStatus.PENDING, Set.of(PaymentStatus.PROCESSING, PaymentStatus.CANCELLED),
            PaymentStatus.PROCESSING, Set.of(PaymentStatus.SUCCESS, PaymentStatus.FAILED, PaymentStatus.CANCELLED),
            PaymentStatus.SUCCESS, Set.of(PaymentStatus.REFUND_PENDING),
            PaymentStatus.FAILED, Set.of(),
            PaymentStatus.CANCELLED, Set.of(),
            PaymentStatus.REFUND_PENDING, Set.of(PaymentStatus.REFUNDED, PaymentStatus.REFUND_FAILED),
            PaymentStatus.REFUNDED, Set.of(),
            PaymentStatus.REFUND_FAILED, Set.of(PaymentStatus.REFUND_PENDING)
    );

    @Test
    void 정의된_결제_상태_전이만_허용한다() {
        for (PaymentStatus currentStatus : PaymentStatus.values()) {
            for (PaymentStatus nextStatus : PaymentStatus.values()) {
                boolean expected = ALLOWED_TRANSITIONS.get(currentStatus).contains(nextStatus);

                assertThat(currentStatus.canTransitionTo(nextStatus))
                        .as("%s -> %s", currentStatus, nextStatus)
                        .isEqualTo(expected);
            }
        }
    }

    @Test
    void 결제성공에서_결제실패로_직접_전이할_수_없다() {
        assertThat(PaymentStatus.SUCCESS.canTransitionTo(PaymentStatus.FAILED)).isFalse();

        assertThatThrownBy(() -> PaymentStatus.SUCCESS.validateTransition(PaymentStatus.FAILED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SUCCESS -> FAILED");
    }

    @Test
    void 환불실패_후에는_환불을_재시도할_수_있다() {
        assertThat(PaymentStatus.REFUND_FAILED.canTransitionTo(PaymentStatus.REFUND_PENDING)).isTrue();
    }

    @Test
    void 동일_상태와_null_상태로의_전이를_거부한다() {
        for (PaymentStatus status : PaymentStatus.values()) {
            assertThat(status.canTransitionTo(status)).isFalse();
            assertThat(status.canTransitionTo(null)).isFalse();
        }
    }

    @Test
    void 엔티티는_금지된_전이에서_기존_상태를_유지한다() {
        Payment payment = Payment.builder()
                .paymentNumber("PAYMENT-1")
                .orderId(1L)
                .memberId(1L)
                .idempotencyKey("idempotency-key")
                .pgProvider("TEST_PG")
                .paymentMethod("CARD")
                .amount(10000)
                .build();
        payment.changeStatus(PaymentStatus.PROCESSING);
        payment.changeStatus(PaymentStatus.SUCCESS);

        assertThatThrownBy(() -> payment.changeStatus(PaymentStatus.FAILED))
                .isInstanceOf(IllegalStateException.class);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getApprovedAt()).isNotNull();
        assertThat(payment.getFailedAt()).isNull();
    }
}
