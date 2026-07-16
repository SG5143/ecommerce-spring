package com.lsg.mingler.domain.member.dto;

/**
 * 회원정보 수정 요청. 폼이 프리필한 전체 편집 대상 값을 담아 통째로 덮어쓴다(아이디·휴대폰 제외).
 * 비밀번호 변경은 별도 엔드포인트(/me/password)에서 처리한다.
 */
public record MemberUpdateRequest(
        String name,
        String phone,
        String email,
        String zipcode,
        String address,
        String addressDetail,
        Integer birthYear,
        Integer birthMonth,
        Integer birthDay,
        boolean marketingAgreed,
        boolean emailAgreed,
        boolean smsAgreed
) {
}
