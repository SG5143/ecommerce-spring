package com.lsg.mingler.domain.member.service;

import com.lsg.mingler.domain.member.dao.MemberRepository;
import com.lsg.mingler.domain.member.dto.MemberCheckIdResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private MemberService memberService;

    @Test
    void 사용가능한_아이디면_passed가_true다() {
        when(memberRepository.existsByUsername("newUser")).thenReturn(false);

        MemberCheckIdResponse response = memberService.checkId("newUser");

        assertThat(response.passed()).isTrue();
        assertThat(response.msg()).isEqualTo("newUser는 사용가능한 아이디 입니다.");
    }

    @Test
    void 이미_사용중인_아이디면_passed가_false다() {
        when(memberRepository.existsByUsername("existingUser")).thenReturn(true);

        MemberCheckIdResponse response = memberService.checkId("existingUser");

        assertThat(response.passed()).isFalse();
        assertThat(response.msg()).isEqualTo("existingUser는 사용중인 아이디 입니다.");
    }

    @Test
    void 아이디가_null이면_예외가_발생한다() {
        assertThatThrownBy(() -> memberService.checkId(null))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void 아이디가_공백이면_예외가_발생한다() {
        assertThatThrownBy(() -> memberService.checkId("   "))
                .isInstanceOf(ResponseStatusException.class);
    }

}
