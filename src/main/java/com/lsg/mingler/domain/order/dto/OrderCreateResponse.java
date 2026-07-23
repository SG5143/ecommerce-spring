package com.lsg.mingler.domain.order.dto;

import com.lsg.mingler.domain.order.entity.OrderStatus;

import java.util.List;

/**
 * 결제 전 주문서 생성 결과.
 * guestOrderToken은 비회원 주문에서만 원문을 한 번 반환하며 회원 주문에서는 null이다.
 */
public record OrderCreateResponse(
        String orderNumber, // 주문번호
        OrderStatus status, // 주문상태
        Integer merchandiseAmount, // 상품금액 합계
        Integer discountAmount, // 할인금액 합계
        Integer shippingFee, // 배송비
        Integer totalAmount, // 최종 결제금액
        List<Item> items, // 주문 상품 목록
        String guestOrderToken // 비회원 조회 토큰
) {

    public record Item(
            Long orderItemId, // 주문 상품 ID
            Long productId, // 상품 ID
            Long optionId, // 옵션 ID
            String productName, // 상품명
            String categoryName, // 카테고리명
            String optionName, // 옵션명
            String thumbnailUrl, // 썸네일 URL
            Integer unitPrice, // 단가
            Integer quantity, // 수량
            Integer lineAmount // 항목 합계금액
    ) {
    }
}
