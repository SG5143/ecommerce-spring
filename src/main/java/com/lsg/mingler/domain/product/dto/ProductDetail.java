package com.lsg.mingler.domain.product.dto;

import java.util.List;

/** 상품 상세 페이지 뷰 모델: 가격·이미지·옵션과 품절 여부 */
public record ProductDetail(Long id, Long categoryId, String name, String description,
                            int price, Integer salePrice, int stockQuantity, boolean soldOut,
                            List<String> imageUrls, List<Option> options) {

    /** 상품 옵션 한 줄: 추가금액과 옵션별 재고 */
    public record Option(Long id, String name, int extraPrice, int stockQuantity) {

        public boolean soldOut() {
            return stockQuantity <= 0;
        }

    }

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

    /** 옵션 상품 여부 (없으면 단일 구성으로 바로 수량 선택) */
    public boolean hasOptions() {
        return !options.isEmpty();
    }

}
