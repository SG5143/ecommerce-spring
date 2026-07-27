package com.lsg.mingler.domain.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "payment", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payment_member_idempotency", columnNames = {"member_id", "idempotency_key"}),
        @UniqueConstraint(name = "uk_payment_order_idempotency", columnNames = {"order_id", "idempotency_key"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_number", nullable = false, unique = true, length = 50)
    private String paymentNumber;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "pg_provider", nullable = false, length = 30)
    private String pgProvider;

    @Column(name = "payment_method", nullable = false, length = 30)
    private String paymentMethod;

    @Column(nullable = false)
    private Integer amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentStatus status;

    @Column(name = "pg_transaction_key", unique = true, length = 100)
    private String pgTransactionKey;

    @Column(name = "failure_code", length = 50)
    private String failureCode;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "refund_requested_at")
    private LocalDateTime refundRequestedAt;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    @Column(name = "refund_failed_at")
    private LocalDateTime refundFailedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private Payment(String paymentNumber, Long orderId, Long memberId,
                    String idempotencyKey, String pgProvider,
                    String paymentMethod, Integer amount) {
        this.paymentNumber = paymentNumber;
        this.orderId = orderId;
        this.memberId = memberId;
        this.idempotencyKey = idempotencyKey;
        this.pgProvider = pgProvider;
        this.paymentMethod = paymentMethod;
        this.amount = amount;
        this.status = PaymentStatus.PENDING;
    }

    public void recordTransactionKey(String pgTransactionKey) {
        this.pgTransactionKey = pgTransactionKey;
    }

    public void recordFailure(String failureCode, String failureReason) {
        this.failureCode = failureCode;
        this.failureReason = failureReason;
    }

    public void changeStatus(PaymentStatus nextStatus) {
        status.validateTransition(nextStatus);  // 결제 상태를 바꾸는게 가능한지 검사 -> 허용되지 않을 경우 예외 발생
        this.status = nextStatus; // 위에서 검증 통과 시 실제 상태를 변경

        LocalDateTime now = LocalDateTime.now();
        switch (nextStatus) {   // 상태가 바뀐 시각을 now 로 기록
            case SUCCESS -> this.approvedAt = now;
            case FAILED -> this.failedAt = now;
            case CANCELLED -> this.cancelledAt = now;
            case REFUND_PENDING -> this.refundRequestedAt = now;
            case REFUNDED -> this.refundedAt = now;
            case REFUND_FAILED -> this.refundFailedAt = now;
            case PENDING -> {
            }
        }
    }
}
