package com.lsg.mingler.domain.order.dto;

import java.util.List;

/**
 * 선택한 장바구니 항목으로 주문서를 생성하는 요청.
 * orderer는 비회원에게만 필수이며 receiver는 회원 여부와 관계없이 직접 입력
 */
public record OrderCreateRequest(
        List<Long> cartItemIds, // 선택한 장바구니 상품 ID 목록
        Orderer orderer, // 주문자 정보
        Receiver receiver, // 수령인 정보
        String deliveryMessage // 배송 요청사항
) {

    public record Orderer(
            String name, // 주문자명
            String phone, // 주문자 연락처
            String email // 주문자 이메일
    ) {
    }

    public record Receiver(
            String name, // 수령인명
            String phone, // 수령인 연락처
            String zipcode, // 우편번호
            String address, // 기본주소
            String addressDetail // 상세주소
    ) {
    }
}
