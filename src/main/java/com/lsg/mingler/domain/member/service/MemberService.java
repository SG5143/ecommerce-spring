package com.lsg.mingler.domain.member.service;

import com.lsg.mingler.domain.auth.service.AuthService;
import com.lsg.mingler.domain.member.dao.MemberAddressRepository;
import com.lsg.mingler.domain.member.dao.MemberRepository;
import com.lsg.mingler.domain.member.dto.MemberCheckIdResponse;
import com.lsg.mingler.domain.member.dto.MemberDetailResponse;
import com.lsg.mingler.domain.member.dto.MemberPasswordUpdateRequest;
import com.lsg.mingler.domain.member.dto.MemberSignupRequest;
import com.lsg.mingler.domain.member.dto.MemberUpdateRequest;
import com.lsg.mingler.domain.member.dto.MemberSummaryResponse;
import com.lsg.mingler.domain.member.entity.Member;
import com.lsg.mingler.domain.member.entity.MemberAddress;
import com.lsg.mingler.global.error.AuthenticationException;
import com.lsg.mingler.global.error.DuplicateException;
import com.lsg.mingler.global.validation.InputValidator;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberService {

    private static final int MINIMUM_AGE = 14;

    private final MemberRepository memberRepository;
    private final MemberAddressRepository memberAddressRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;

    public MemberCheckIdResponse checkId(String memberId) {
        String error = InputValidator.memberIdError(memberId);
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

    /**
     * 회원정보 수정 페이지용 상세 조회. 기본 배송지가 없으면 주소 필드는 null 로 내려감
     */
    @Transactional(readOnly = true)
    public MemberDetailResponse getDetail(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new AuthenticationException("회원 정보를 찾을 수 없습니다. 다시 로그인해주세요."));

        MemberAddress address = memberAddressRepository.findByMemberIdAndIsDefaultTrue(memberId).orElse(null);

        return new MemberDetailResponse(
                member.getUsername(),
                member.getName(),
                member.getPhone(),
                member.getBirthDate() != null ? member.getBirthDate().toString() : null,
                address != null ? address.getZipcode() : null,
                address != null ? address.getAddress() : null,
                address != null ? address.getAddressDetail() : null,
                Boolean.TRUE.equals(member.getMarketingAgreed()),
                member.getEmail(),
                Boolean.TRUE.equals(member.getEmailAgreed()),
                Boolean.TRUE.equals(member.getSmsAgreed()),
                member.getPointBalance()
        );
    }

    @Transactional
    public void signup(MemberSignupRequest request) {
        String idError = InputValidator.memberIdError(request.username());
        if (idError != null) {
            throw new IllegalArgumentException(idError);
        }

        InputValidator.validatePassword(request.password(), request.passwordConfirm());
        InputValidator.requirePresent(request.name(), "이름을 입력해주세요.");
        InputValidator.requirePresent(request.phone(), "핸드폰 본인확인을 진행해주세요.");
        InputValidator.requirePresent(request.zipcode(), "우편번호를 입력해주세요.");
        InputValidator.requirePresent(request.address(), "주소를 입력해주세요.");

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

    /**
     * 회원정보 수정. 편집 대상 값을 통째로 덮어쓴다(아이디·휴대폰 제외).
     * 마케팅 미동의 시 이메일·SMS 수신동의도 강제로 false 처리(프론트 잠금과 대칭).
     */
    @Transactional
    public void updateProfile(Long memberId, MemberUpdateRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new AuthenticationException("회원 정보를 찾을 수 없습니다. 다시 로그인해주세요."));

        InputValidator.requirePresent(request.name(), "이름을 입력해주세요.");
        InputValidator.requirePresent(request.phone(), "휴대폰 번호를 입력해주세요.");
        InputValidator.validateOptionalEmail(request.email(), "이메일 형식을 확인해주세요.");

        String phone = request.phone().replaceAll("[^0-9]", "");
        if (!phone.equals(member.getPhone()) && memberRepository.existsByPhone(phone)) {
            throw new DuplicateException("이미 등록된 휴대폰 번호입니다.");
        }

        LocalDate birthDate = parseBirthDate(request.birthYear(), request.birthMonth(), request.birthDay());
        if (birthDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("생년월일은 오늘까지만 입력할 수 있습니다.");
        }
        if (Period.between(birthDate, LocalDate.now()).getYears() < MINIMUM_AGE) {
            throw new IllegalArgumentException("만 14세 이상만 이용할 수 있습니다.");
        }

        boolean marketingAgreed = request.marketingAgreed();
        boolean emailAgreed = marketingAgreed && request.emailAgreed();
        boolean smsAgreed = marketingAgreed && request.smsAgreed();

        member.updateProfile(request.name(), phone, request.email(), birthDate, marketingAgreed, emailAgreed, smsAgreed);

        memberAddressRepository.findByMemberIdAndIsDefaultTrue(memberId)
                .ifPresent(address -> address.updateAddress(request.zipcode(), request.address(), request.addressDetail()));
    }

    /**
     * 비밀번호 변경. 현재 비밀번호를 대조한 뒤 새 비밀번호로 교체.
     */
    @Transactional
    public void changePassword(Long memberId, MemberPasswordUpdateRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new AuthenticationException("회원 정보를 찾을 수 없습니다. 다시 로그인해주세요."));

        InputValidator.requirePresent(request.currentPassword(), "현재 비밀번호를 입력해주세요.");
        if (!passwordEncoder.matches(request.currentPassword(), member.getPassword())) {
            throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다.");
        }

        InputValidator.validatePassword(request.newPassword(), request.newPasswordConfirm());
        if (request.currentPassword().equals(request.newPassword())) {
            throw new IllegalArgumentException("새 비밀번호가 현재 비밀번호와 같습니다.");
        }

        member.changePassword(passwordEncoder.encode(request.newPassword()));

        // 비밀번호 변경은 보안 이벤트 → 발급된 모든 Refresh 토큰을 폐기해 전체 로그아웃 처리
        authService.revokeAllTokens(memberId);
    }

    /**
     * 회원 탈퇴(소프트 삭제). 레코드는 유지해 동일 아이디·SNS 재가입을 차단하고,
     * 보유 적립금을 소멸시킨 뒤 모든 Refresh 토큰을 폐기해 전체 로그아웃한다.
     */
    @Transactional
    public void withdraw(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new AuthenticationException("회원 정보를 찾을 수 없습니다. 다시 로그인해주세요."));

        member.withdraw();
        authService.revokeAllTokens(memberId);
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
