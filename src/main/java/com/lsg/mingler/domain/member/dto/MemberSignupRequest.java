package com.lsg.mingler.domain.member.dto;

public record MemberSignupRequest(
        String username,
        String password,
        String passwordConfirm,
        String name,
        String phone,
        String zipcode,
        String address,
        String addressDetail,
        Integer birthYear,
        Integer birthMonth,
        Integer birthDay,
        boolean termsAgreed,
        boolean privacyAgreed,
        boolean ageAgreed,
        boolean marketingAgreed
) {
}
