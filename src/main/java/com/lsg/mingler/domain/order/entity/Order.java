package com.lsg.mingler.domain.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number", nullable = false, unique = true, length = 50)
    private String orderNumber;

    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "guest_token_hash", unique = true, length = 64)
    private String guestTokenHash;

    @Column(name = "orderer_name", nullable = false, length = 50)
    private String ordererName;

    @Column(name = "orderer_phone", nullable = false, length = 20)
    private String ordererPhone;

    @Column(name = "orderer_email", length = 255)
    private String ordererEmail;

    @Column(name = "receiver_name", nullable = false, length = 50)
    private String receiverName;

    @Column(name = "receiver_phone", nullable = false, length = 20)
    private String receiverPhone;

    @Column(nullable = false, length = 10)
    private String zipcode;

    @Column(nullable = false, length = 255)
    private String address;

    @Column(name = "address_detail", length = 255)
    private String addressDetail;

    @Column(name = "delivery_message", length = 255)
    private String deliveryMessage;

    @Column(name = "merchandise_amount", nullable = false)
    private Integer merchandiseAmount;

    @Column(name = "discount_amount", nullable = false)
    private Integer discountAmount;

    @Column(name = "shipping_fee", nullable = false)
    private Integer shippingFee;

    @Column(name = "total_amount", nullable = false)
    private Integer totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "shipping_started_at")
    private LocalDateTime shippingStartedAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "return_requested_at")
    private LocalDateTime returnRequestedAt;

    @Column(name = "returned_at")
    private LocalDateTime returnedAt;

    @Column(name = "expired_at")
    private LocalDateTime expiredAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private Order(String orderNumber, Long memberId, String guestTokenHash,
                  String ordererName, String ordererPhone, String ordererEmail,
                  String receiverName, String receiverPhone, String zipcode,
                  String address, String addressDetail, String deliveryMessage,
                  Integer merchandiseAmount, Integer discountAmount,
                  Integer shippingFee, Integer totalAmount) {
        this.orderNumber = orderNumber;
        this.memberId = memberId;
        this.guestTokenHash = guestTokenHash;
        this.ordererName = ordererName;
        this.ordererPhone = ordererPhone;
        this.ordererEmail = ordererEmail;
        this.receiverName = receiverName;
        this.receiverPhone = receiverPhone;
        this.zipcode = zipcode;
        this.address = address;
        this.addressDetail = addressDetail;
        this.deliveryMessage = deliveryMessage;
        this.merchandiseAmount = merchandiseAmount;
        this.discountAmount = discountAmount != null ? discountAmount : 0;
        this.shippingFee = shippingFee != null ? shippingFee : 0;
        this.totalAmount = totalAmount;
        this.status = OrderStatus.PENDING_PAYMENT;
    }

    public void changeStatus(OrderStatus nextStatus) {
        status.validateTransition(nextStatus); // 주문 상태를 바꾸는게 가능한지 검사 -> 허용되지 않을 경우 예외 발생
        this.status = nextStatus; // 위에서 검증 통과 시 실제 상태를 변경

        LocalDateTime now = LocalDateTime.now();
        switch (nextStatus) { // 상태가 바뀐 시각을 now 로 기록
            case PAID -> this.paidAt = now;
            case SHIPPING -> this.shippingStartedAt = now;
            case DELIVERED -> this.deliveredAt = now;
            case CANCELLED -> this.cancelledAt = now;
            case RETURN_REQUESTED -> this.returnRequestedAt = now;
            case RETURNED -> this.returnedAt = now;
            case EXPIRED -> this.expiredAt = now;
            case PENDING_PAYMENT, PREPARING -> {
            }
        }
    }
}
