package com.lsg.mingler.domain.member.service;

import com.lsg.mingler.domain.auth.service.AuthService;
import com.lsg.mingler.domain.member.dao.MemberAddressRepository;
import com.lsg.mingler.domain.member.dao.MemberRepository;
import com.lsg.mingler.domain.member.dto.MemberCheckIdResponse;
import com.lsg.mingler.domain.member.dto.MemberPasswordUpdateRequest;
import com.lsg.mingler.domain.member.dto.MemberUpdateRequest;
import com.lsg.mingler.domain.member.entity.Member;
import com.lsg.mingler.global.error.DuplicateException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MemberAddressRepository memberAddressRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthService authService;

    @InjectMocks
    private MemberService memberService;

    private Member sampleMember() {
        LocalDateTime now = LocalDateTime.now();
        return Member.builder()
                .username("tester1")
                .password("ENCODED")
                .name("홍길동")
                .phone("01012345678")
                .birthDate(LocalDate.of(1990, 1, 1))
                .marketingAgreed(true)
                .marketingAgreedAt(now)
                .termsAgreedAt(now)
                .privacyAgreedAt(now)
                .build();
    }

    private MemberUpdateRequest updateRequest(String name, String email, LocalDate birth,
                                              boolean marketing, boolean emailAgreed, boolean smsAgreed) {
        return new MemberUpdateRequest(name, "01012345678", email, "12345", "서울시 어딘가", "101동 101호",
                birth.getYear(), birth.getMonthValue(), birth.getDayOfMonth(),
                marketing, emailAgreed, smsAgreed);
    }

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

    @Test
    void 이메일_형식이_틀리면_수정에_실패한다() {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(sampleMember()));

        MemberUpdateRequest request = updateRequest("홍길동", "invalid-email", LocalDate.of(1990, 1, 1), true, false, false);

        assertThatThrownBy(() -> memberService.updateProfile(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이메일 형식을 확인해주세요.");
    }

    @Test
    void 이메일이_비어있으면_수정에_통과한다() {
        Member member = sampleMember();
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(memberAddressRepository.findByMemberIdAndIsDefaultTrue(1L)).thenReturn(Optional.empty());

        MemberUpdateRequest request = updateRequest("홍길동", "", LocalDate.of(1990, 1, 1), true, true, true);
        memberService.updateProfile(1L, request);

        assertThat(member.getEmail()).isEmpty();
        assertThat(member.getEmailAgreed()).isTrue();
    }

    @Test
    void 휴대폰이_다른_회원과_중복이면_수정에_실패한다() {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(sampleMember()));
        when(memberRepository.existsByPhone("01099998888")).thenReturn(true);

        MemberUpdateRequest request = new MemberUpdateRequest("홍길동", "010-9999-8888", "user@mingler.com",
                "12345", "서울시 어딘가", "101동 101호", 1990, 1, 1, true, false, false);

        assertThatThrownBy(() -> memberService.updateProfile(1L, request))
                .isInstanceOf(DuplicateException.class)
                .hasMessage("이미 등록된 휴대폰 번호입니다.");
    }

    @Test
    void 만14세_미만이면_수정에_실패한다() {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(sampleMember()));

        LocalDate underage = LocalDate.now().minusYears(13);
        MemberUpdateRequest request = updateRequest("홍길동", "user@mingler.com", underage, true, false, false);

        assertThatThrownBy(() -> memberService.updateProfile(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("만 14세 이상만 이용할 수 있습니다.");
    }

    @Test
    void 마케팅_미동의면_이메일SMS수신도_false로_저장된다() {
        Member member = sampleMember();
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(memberAddressRepository.findByMemberIdAndIsDefaultTrue(1L)).thenReturn(Optional.empty());

        MemberUpdateRequest request = updateRequest("홍길동", "user@mingler.com", LocalDate.of(1990, 1, 1), false, true, true);
        memberService.updateProfile(1L, request);

        assertThat(member.getMarketingAgreed()).isFalse();
        assertThat(member.getEmailAgreed()).isFalse();
        assertThat(member.getSmsAgreed()).isFalse();
    }

    @Test
    void 현재_비밀번호가_틀리면_변경에_실패한다() {
        Member member = sampleMember();
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("wrongPw1!", "ENCODED")).thenReturn(false);

        MemberPasswordUpdateRequest request = new MemberPasswordUpdateRequest("wrongPw1!", "NewPass12!", "NewPass12!");

        assertThatThrownBy(() -> memberService.changePassword(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("현재 비밀번호가 일치하지 않습니다.");
    }

    @Test
    void 새_비밀번호_확인이_다르면_변경에_실패한다() {
        Member member = sampleMember();
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("current1!", "ENCODED")).thenReturn(true);

        MemberPasswordUpdateRequest request = new MemberPasswordUpdateRequest("current1!", "NewPass12!", "Different99!");

        assertThatThrownBy(() -> memberService.changePassword(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("비밀번호가 일치하지 않습니다.");
    }

    @Test
    void 비밀번호_변경_성공시_모든_리프레시토큰을_폐기한다() {
        Member member = sampleMember();
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("current1!", "ENCODED")).thenReturn(true);

        MemberPasswordUpdateRequest request = new MemberPasswordUpdateRequest("current1!", "NewPass12!", "NewPass12!");
        memberService.changePassword(1L, request);

        verify(authService).revokeAllTokens(1L);
    }

    @Test
    void 탈퇴하면_상태가_WITHDRAWN이_되고_토큰이_폐기된다() {
        Member member = sampleMember();
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        memberService.withdraw(1L);

        assertThat(member.getStatus()).isEqualTo("WITHDRAWN");
        assertThat(member.getWithdrawnAt()).isNotNull();
        assertThat(member.getPointBalance()).isZero();
        verify(authService).revokeAllTokens(1L);
    }

}
