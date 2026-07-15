package com.lsg.mingler.domain.member.service;

import com.lsg.mingler.domain.member.dao.MemberAddressRepository;
import com.lsg.mingler.domain.member.dao.MemberRepository;
import com.lsg.mingler.domain.member.dto.MemberCheckIdResponse;
import com.lsg.mingler.domain.member.dto.MemberSignupRequest;
import com.lsg.mingler.domain.member.dto.MemberSummaryResponse;
import com.lsg.mingler.domain.member.entity.Member;
import com.lsg.mingler.domain.member.entity.MemberAddress;
import com.lsg.mingler.global.error.AuthenticationException;
import com.lsg.mingler.global.error.DuplicateException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberService {

    private static final int MEMBER_ID_MIN_LENGTH = 4;
    private static final int MEMBER_ID_MAX_LENGTH = 16;
    private static final Pattern MEMBER_ID_PATTERN = Pattern.compile("^[a-z][a-z0-9._]*$");
    private static final Pattern PASSWORD_ALLOWED_PATTERN = Pattern.compile("^[a-zA-Z0-9!@#$%^*+=.\\-]{8,20}$");
    private static final Pattern[] PASSWORD_CHAR_GROUPS = {
            Pattern.compile("[a-zA-Z]"),
            Pattern.compile("[0-9]"),
            Pattern.compile("[!@#$%^*+=.\\-]")
    };
    private static final int MINIMUM_AGE = 14;

    private final MemberRepository memberRepository;
    private final MemberAddressRepository memberAddressRepository;
    private final PasswordEncoder passwordEncoder;

    public MemberCheckIdResponse checkId(String memberId) {
        String error = validateMemberIdFormat(memberId);
        if (error != null) {
            return new MemberCheckIdResponse(error, false);
        }

        boolean passed = !memberRepository.existsByUsername(memberId);
        String msg = passed
                ? memberId + "는 사용가능한 아이디 입니다."
                : memberId + "는 사용중인 아이디 입니다.";

        return new MemberCheckIdResponse(msg, passed);
    }

    /**
     * 마이샵 요약 정보 조회. 유효한 토큰이지만 회원이 없으면 인증 오류로 간주
     * 총 구매 금액·쿠폰 수는 주문·쿠폰 도메인 미구현이라 0 으로 반환
     */
    @Transactional(readOnly = true)
    public MemberSummaryResponse getSummary(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new AuthenticationException("회원 정보를 찾을 수 없습니다. 다시 로그인해주세요."));

        return new MemberSummaryResponse(
                member.getName(),
                member.getGrade(),
                0L,
                member.getPointBalance(),
                0
        );
    }

    @Transactional
    public void signup(MemberSignupRequest request) {
        String idError = validateMemberIdFormat(request.username());
        if (idError != null) {
            throw new IllegalArgumentException(idError);
        }

        validatePassword(request.password(), request.passwordConfirm());
        validateRequired(request.name(), "이름을 입력해주세요.");
        validateRequired(request.phone(), "핸드폰 본인확인을 진행해주세요.");
        validateRequired(request.zipcode(), "우편번호를 입력해주세요.");
        validateRequired(request.address(), "주소를 입력해주세요.");

        LocalDate birthDate = parseBirthDate(request.birthYear(), request.birthMonth(), request.birthDay());
        if (Period.between(birthDate, LocalDate.now()).getYears() < MINIMUM_AGE) {
            throw new IllegalArgumentException("만 14세 이상만 가입할 수 있습니다.");
        }

        if (!request.termsAgreed() || !request.privacyAgreed() || !request.ageAgreed()) {
            throw new IllegalArgumentException("필수 약관에 모두 동의해주세요.");
        }

        String phone = request.phone().replaceAll("[^0-9]", "");

        if (memberRepository.existsByUsername(request.username())) {
            throw new DuplicateException("이미 사용중인 아이디입니다.");
        }
        if (memberRepository.existsByPhone(phone)) {
            throw new DuplicateException("이미 등록된 휴대폰 번호입니다.");
        }

        LocalDateTime now = LocalDateTime.now();
        Member member = Member.builder()
                .username(request.username())
                .password(passwordEncoder.encode(request.password()))
                .name(request.name())
                .phone(phone)
                .birthDate(birthDate)
                .marketingAgreed(request.marketingAgreed())
                .marketingAgreedAt(request.marketingAgreed() ? now : null)
                .termsAgreedAt(now)
                .privacyAgreedAt(now)
                .build();
        Member savedMember = memberRepository.save(member);

        MemberAddress address = MemberAddress.builder()
                .memberId(savedMember.getId())
                .receiverName(request.name())
                .receiverPhone(phone)
                .zipcode(request.zipcode())
                .address(request.address())
                .addressDetail(request.addressDetail())
                .isDefault(true)
                .build();
        memberAddressRepository.save(address);
    }

    private String validateMemberIdFormat(String memberId) {
        if (memberId == null || memberId.isBlank()) {
            return "아이디는 필수입니다.";
        }
        if (memberId.length() < MEMBER_ID_MIN_LENGTH || memberId.length() > MEMBER_ID_MAX_LENGTH) {
            return "아이디는 4~16자여야 합니다.";
        }
        if (!MEMBER_ID_PATTERN.matcher(memberId).matches()) {
            return "아이디는 소문자 영문으로 시작하는 소문자/숫자/.,_ 조합이어야 합니다.";
        }
        return null;
    }

    private void validatePassword(String password, String passwordConfirm) {
        if (password == null || !PASSWORD_ALLOWED_PATTERN.matcher(password).matches()) {
            throw new IllegalArgumentException("비밀번호 형식을 확인해주세요.");
        }
        long groupCount = 0;
        for (Pattern group : PASSWORD_CHAR_GROUPS) {
            if (group.matcher(password).find()) {
                groupCount++;
            }
        }
        if (groupCount < 2) {
            throw new IllegalArgumentException("비밀번호 형식을 확인해주세요.");
        }
        if (!password.equals(passwordConfirm)) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }
    }

    private void validateRequired(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    private LocalDate parseBirthDate(Integer year, Integer month, Integer day) {
        if (year == null || month == null || day == null) {
            throw new IllegalArgumentException("생년월일을 정확히 선택해주세요.");
        }
        try {
            return LocalDate.of(year, month, day);
        } catch (java.time.DateTimeException e) {
            throw new IllegalArgumentException("생년월일을 정확히 선택해주세요.");
        }
    }

}
