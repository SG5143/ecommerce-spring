package com.lsg.mingler.domain.member.api;

import com.lsg.mingler.domain.member.dao.MemberRepository;
import com.lsg.mingler.domain.member.dto.MemberCheckIdResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/members")
public class MemberApiController {

    private final MemberRepository memberRepository;

    public MemberApiController(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @GetMapping("/check-id")
    public ResponseEntity<MemberCheckIdResponse> checkId(@RequestParam String username) {
        if (username == null || username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "아이디는 필수입니다.");
        }

        boolean available = !memberRepository.existsByUsername(username);
        return ResponseEntity.ok(new MemberCheckIdResponse(username, available));
    }

}
