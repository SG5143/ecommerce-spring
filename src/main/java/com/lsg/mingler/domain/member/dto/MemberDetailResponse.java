package com.lsg.mingler.domain.member.dto;

/**
 * 회원정보 수정 페이지 프리필용 상세 정보.
 * 회원가입 시 입력한 값들을 담는다(아이디는 수정 불가). 주소는 기본 배송지(member_address) 기준.
 * birthDate 는 ISO 형식(yyyy-MM-dd) 문자열로 내려 텍스트 입력창에 그대로 채운다.
 */
public record MemberDetailResponse(
        String username,
        String name,
        String phone,
        String birthDate,
        String zipcode,
        String address,
        String addressDetail,
        boolean marketingAgreed,
        String email,
        boolean emailAgreed,
        boolean smsAgreed
) {
}
