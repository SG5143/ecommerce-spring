package com.lsg.mingler.domain.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "product_option")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "extra_price", nullable = false)
    private Integer extraPrice;

    @Column(name = "stock_quantity", nullable = false)
    private Integer stockQuantity;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "is_default", nullable = false)
    private Boolean isDefault;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private ProductOption(Long productId, String name, Integer extraPrice,
                          Integer stockQuantity, Integer displayOrder, Boolean isActive, Boolean isDefault) {
        this.productId = productId;
        this.name = name;
        this.extraPrice = extraPrice != null ? extraPrice : 0;
        this.stockQuantity = stockQuantity != null ? stockQuantity : 0;
        this.displayOrder = displayOrder != null ? displayOrder : 0;
        this.isActive = isActive == null || isActive;
        this.isDefault = Boolean.TRUE.equals(isDefault);
    }

    public boolean isSoldOut() {
        return stockQuantity <= 0;
    }

    /**
     * 상품 옵션의 현재 재고에서 주문 수량을 차감한다.
     *
     * @param quantity 차감할 수량
     * @throws IllegalArgumentException 차감 수량이 0 이하인 경우
     * @throws IllegalStateException 현재 옵션 재고보다 많은 수량을 차감하려는 경우
     */
    public void decreaseStock(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("차감 수량은 0보다 커야 합니다.");
        }
        if (stockQuantity < quantity) {
            throw new IllegalStateException("상품 옵션 재고가 부족합니다.");
        }
        this.stockQuantity -= quantity;
    }

    /**
     * 결제 승인 실패 또는 취소 시 예약했던 주문 수량을 현재 재고에 복구한다.
     *
     * @param quantity 복구할 수량
     * @throws IllegalArgumentException 복구 수량이 0 이하인 경우
     * @throws ArithmeticException 복구 결과가 {@link Integer}의 최댓값을 초과하는 경우
     */
    public void increaseStock(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("복구 수량은 0보다 커야 합니다.");
        }
        this.stockQuantity = Math.addExact(this.stockQuantity, quantity);
    }
}
