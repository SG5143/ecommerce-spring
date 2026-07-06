package com.lsg.mingler.domain.member.service;

import com.lsg.mingler.domain.member.dao.MemberRepository;
import com.lsg.mingler.domain.member.dto.MemberCheckIdResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private MemberService memberService;

    @Test
    void 사용가능한_아이디면_passed가_true다() {
        when(memberRepository.existsByUsername("newuser")).thenReturn(false);

        MemberCheckIdResponse response = memberService.checkId("newuser");

        assertThat(response.passed()).isTrue();
        assertThat(response.msg()).isEqualTo("newuser는 사용가능한 아이디 입니다.");
    }

    @Test
    void 이미_사용중인_아이디면_passed가_false다() {
        when(memberRepository.existsByUsername("existinguser")).thenReturn(true);

        MemberCheckIdResponse response = memberService.checkId("existinguser");

        assertThat(response.passed()).isFalse();
        assertThat(response.msg()).isEqualTo("existinguser는 사용중인 아이디 입니다.");
    }

    @Test
    void 아이디가_null이면_passed가_false다() {
        MemberCheckIdResponse response = memberService.checkId(null);

        assertThat(response.passed()).isFalse();
        assertThat(response.msg()).isEqualTo("아이디는 필수입니다.");
    }

    @Test
    void 아이디가_공백이면_passed가_false다() {
        MemberCheckIdResponse response = memberService.checkId("   ");

        assertThat(response.passed()).isFalse();
        assertThat(response.msg()).isEqualTo("아이디는 필수입니다.");
    }

    @Test
    void 아이디_길이가_4자_미만이면_passed가_false다() {
        MemberCheckIdResponse response = memberService.checkId("abc");

        assertThat(response.passed()).isFalse();
        assertThat(response.msg()).isEqualTo("아이디는 4~16자여야 합니다.");
    }

    @Test
    void 아이디_길이가_16자_초과이면_passed가_false다() {
        MemberCheckIdResponse response = memberService.checkId("abcdefghijklmnopq");

        assertThat(response.passed()).isFalse();
        assertThat(response.msg()).isEqualTo("아이디는 4~16자여야 합니다.");
    }

    @Test
    void 아이디가_숫자로_시작하면_passed가_false다() {
        MemberCheckIdResponse response = memberService.checkId("1abcd");

        assertThat(response.passed()).isFalse();
        assertThat(response.msg()).isEqualTo("아이디는 소문자 영문으로 시작하는 소문자/숫자/.,_ 조합이어야 합니다.");
    }

    @Test
    void 아이디에_허용되지_않은_문자가_있으면_passed가_false다() {
        MemberCheckIdResponse response = memberService.checkId("abc!def");

        assertThat(response.passed()).isFalse();
        assertThat(response.msg()).isEqualTo("아이디는 소문자 영문으로 시작하는 소문자/숫자/.,_ 조합이어야 합니다.");
    }

}
