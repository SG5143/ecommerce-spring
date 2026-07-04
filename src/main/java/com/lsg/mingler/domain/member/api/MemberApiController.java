package com.lsg.mingler.domain.member.api;

import com.lsg.mingler.domain.member.dto.MemberCheckIdResponse;
import com.lsg.mingler.domain.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberApiController {

    private final MemberService memberService;

    @GetMapping("/check-id")
    public ResponseEntity<MemberCheckIdResponse> checkId(@RequestParam String member_id) {
        return ResponseEntity.ok(memberService.checkId(member_id));
    }

}
