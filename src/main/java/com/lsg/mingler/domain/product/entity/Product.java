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
@Table(name = "product")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    public static final String STATUS_ON_SALE = "ON_SALE";
    public static final String STATUS_SOLD_OUT = "SOLD_OUT";
    public static final String STATUS_HIDDEN = "HIDDEN";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false)
    private Integer price;

    @Column(name = "sale_price")
    private Integer salePrice;

    @Column(name = "stock_quantity", nullable = false)
    private Integer stockQuantity;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "view_count", nullable = false)
    private Integer viewCount;

    @Column(name = "sales_count", nullable = false)
    private Integer salesCount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private Product(Long categoryId, String name, String description, Integer price,
                    Integer salePrice, Integer stockQuantity, String thumbnailUrl) {
        this.categoryId = categoryId;
        this.name = name;
        this.description = description;
        this.price = price;
        this.salePrice = salePrice;
        this.stockQuantity = stockQuantity != null ? stockQuantity : 0;
        this.status = STATUS_ON_SALE;
        this.thumbnailUrl = thumbnailUrl;
        this.viewCount = 0;
        this.salesCount = 0;
    }

    /** 노출 가격: 할인가가 있으면 할인가, 없으면 정가 */
    public int getDisplayPrice() {
        return salePrice != null ? salePrice : price;
    }

    public boolean isOnSale() {
        return STATUS_ON_SALE.equals(status);
    }

    /**
     * 상품의 현재 재고에서 주문 수량을 차감한다.
     *
     * @param quantity 차감할 수량
     * @throws IllegalArgumentException 차감 수량이 0 이하인 경우
     * @throws IllegalStateException 현재 재고보다 많은 수량을 차감하려는 경우
     */
    public void decreaseStock(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("차감 수량은 0보다 커야 합니다.");
        }
        if (stockQuantity < quantity) {
            throw new IllegalStateException("상품 재고가 부족합니다.");
        }
        this.stockQuantity -= quantity;
    }
}
