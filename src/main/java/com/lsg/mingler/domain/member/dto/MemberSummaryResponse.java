package com.lsg.mingler.domain.member.dto;

/**
 * 마이샵 상단 요약 정보.
 * - 내정보: 이름, 회원등급, 총 구매 금액
 * - 내 지갑: 적립금, 쿠폰 수
 * 주문·쿠폰 도메인은 아직 미구현이라 totalPurchaseAmount·couponCount 는 0 으로 설정
 */
public record MemberSummaryResponse(
        String name,
        String grade,
        long totalPurchaseAmount,
        int pointBalance,
        int couponCount
) {
}
