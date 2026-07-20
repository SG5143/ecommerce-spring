package com.lsg.mingler.domain.cart.entity;

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
@Table(name = "cart_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cart_id", nullable = false)
    private Long cartId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_option_id")
    private Long productOptionId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price_at_added", nullable = false)
    private Integer unitPriceAtAdded;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private CartItem(Long cartId, Long productId, Long productOptionId,
                     Integer quantity, Integer unitPriceAtAdded) {
        this.cartId = cartId;
        this.productId = productId;
        this.productOptionId = productOptionId;
        this.quantity = quantity;
        this.unitPriceAtAdded = unitPriceAtAdded;
    }

    public void changeQuantity(int quantity) {
        this.quantity = quantity;
    }
}
