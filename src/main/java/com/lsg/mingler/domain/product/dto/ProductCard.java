package com.lsg.mingler.domain.product.dto;

public record ProductCard(Long id, String name, int price, Integer salePrice, String imageUrl) {

    public boolean onSale() {
        return salePrice != null;
    }

    /** 화면에 표시할 최종 가격 (할인가 우선) */
    public int finalPrice() {
        return onSale() ? salePrice : price;
    }

    /** 할인율(%): (정가-할인가)/정가, 반올림 */
    public int discountRate() {
        return Math.round((price - salePrice) * 100f / price);
    }

}
