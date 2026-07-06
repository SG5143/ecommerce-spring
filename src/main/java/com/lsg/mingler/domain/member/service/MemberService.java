package com.lsg.mingler.domain.member.service;

import com.lsg.mingler.domain.member.dao.MemberRepository;
import com.lsg.mingler.domain.member.dto.MemberCheckIdResponse;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MemberService {

    private static final int MEMBER_ID_MIN_LENGTH = 4;
    private static final int MEMBER_ID_MAX_LENGTH = 16;
    private static final Pattern MEMBER_ID_PATTERN = Pattern.compile("^[a-z][a-z0-9._]*$");

    private final MemberRepository memberRepository;

    public MemberCheckIdResponse checkId(String memberId) {
        if (memberId == null || memberId.isBlank()) {
            return new MemberCheckIdResponse("아이디는 필수입니다.", false);
        }
        if (memberId.length() < MEMBER_ID_MIN_LENGTH || memberId.length() > MEMBER_ID_MAX_LENGTH) {
            return new MemberCheckIdResponse("아이디는 4~16자여야 합니다.", false);
        }
        if (!MEMBER_ID_PATTERN.matcher(memberId).matches()) {
            return new MemberCheckIdResponse("아이디는 소문자 영문으로 시작하는 소문자/숫자/.,_ 조합이어야 합니다.", false);
        }

        boolean passed = !memberRepository.existsByUsername(memberId);
        String msg = passed
                ? memberId + "는 사용가능한 아이디 입니다."
                : memberId + "는 사용중인 아이디 입니다.";

        return new MemberCheckIdResponse(msg, passed);
    }

}
