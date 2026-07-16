package com.lsg.mingler.domain.member.api;

import com.lsg.mingler.domain.member.dto.MemberCheckIdResponse;
import com.lsg.mingler.domain.member.dto.MemberDetailResponse;
import com.lsg.mingler.domain.member.dto.MemberPasswordUpdateRequest;
import com.lsg.mingler.domain.member.dto.MemberSignupRequest;
import com.lsg.mingler.domain.member.dto.MemberSignupResponse;
import com.lsg.mingler.domain.member.dto.MemberSummaryResponse;
import com.lsg.mingler.domain.member.dto.MemberUpdateRequest;
import com.lsg.mingler.domain.member.dto.MemberUpdateResponse;
import com.lsg.mingler.domain.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberApiController {

    private final MemberService memberService;

    /**
     * 아이디 중복검사
     */
    @GetMapping("/check-id")
    public ResponseEntity<MemberCheckIdResponse> checkId(@RequestParam String member_id) {
        return ResponseEntity.ok(memberService.checkId(member_id));
    }

    /**
     * 마이샵 요약 정보.
     * 인증된 회원(Access 토큰)만 접근 가능하며, 인증 주체는 memberId
     */
    @GetMapping("/me")
    public ResponseEntity<MemberSummaryResponse> me(@AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(memberService.getSummary(memberId));
    }

    /**
     * 회원정보 수정 페이지용 상세 정보, 인증된 회원 본인만 접근 가능
     */
    @GetMapping("/me/detail")
    public ResponseEntity<MemberDetailResponse> myDetail(@AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(memberService.getDetail(memberId));
    }

    /**
     * 회원가입
     */
    @PostMapping
    public ResponseEntity<MemberSignupResponse> signup(@RequestBody MemberSignupRequest request) {
        memberService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new MemberSignupResponse("회원가입이 완료되었습니다."));
    }

    /**
     * 회원정보 수정, 인증된 회원 본인만 접근 가능
     */
    @PutMapping("/me")
    public ResponseEntity<MemberUpdateResponse> updateMe(@AuthenticationPrincipal Long memberId, @RequestBody MemberUpdateRequest request) {
        memberService.updateProfile(memberId, request);
        return ResponseEntity.ok(new MemberUpdateResponse("회원정보가 수정되었습니다."));
    }

    /**
     * 비밀번호 변경, 인증된 회원 본인만 접근 가능
     */
    @PatchMapping("/me/password")
    public ResponseEntity<MemberUpdateResponse> changePassword(@AuthenticationPrincipal Long memberId, @RequestBody MemberPasswordUpdateRequest request) {
        memberService.changePassword(memberId, request);
        return ResponseEntity.ok(new MemberUpdateResponse("비밀번호가 변경되었습니다."));
    }

}
