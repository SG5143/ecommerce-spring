package com.lsg.mingler.domain.member.service;

import com.lsg.mingler.domain.member.dao.MemberRepository;
import com.lsg.mingler.domain.member.dto.MemberCheckIdResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;

    public MemberCheckIdResponse checkId(String memberId) {
        if (memberId == null || memberId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "아이디는 필수입니다.");
        }

        boolean passed = !memberRepository.existsByUsername(memberId);
        String msg = passed
                ? memberId + "는 사용가능한 아이디 입니다."
                : memberId + "는 사용중인 아이디 입니다.";

        return new MemberCheckIdResponse(msg, passed);
    }

}
