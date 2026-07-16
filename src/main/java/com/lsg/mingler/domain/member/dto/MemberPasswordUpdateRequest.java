package com.lsg.mingler.domain.member.dto;

/**
 * 비밀번호 변경 요청. 현재 비밀번호 대조 후 새 비밀번호로 교체.
 */
public record MemberPasswordUpdateRequest(
        String currentPassword,
        String newPassword,
        String newPasswordConfirm
) {
}
