package com.lsg.mingler.global.validation;

import java.util.regex.Pattern;

/**
 * 문자열 길이와 정규식처럼 입력값만으로 판단할 수 있는 공통 검증을 제공한다.
 *
 * <p>저장소 조회나 도메인 상태가 필요한 검증은 포함하지 않으며, 모든 메서드는 검증 상태를 보관하지 않는다.
 */
public final class InputValidator {

    private static final int MEMBER_ID_MIN_LENGTH = 4;
    private static final int MEMBER_ID_MAX_LENGTH = 16;
    private static final int EMAIL_MAX_LENGTH = 255;

    private static final Pattern MEMBER_ID_PATTERN = Pattern.compile("^[a-z][a-z0-9._]*$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern PASSWORD_ALLOWED_PATTERN = Pattern.compile("^[a-zA-Z0-9!@#$%^*+=.\\-]{8,20}$");
    private static final Pattern[] PASSWORD_CHAR_GROUPS = {
            Pattern.compile("[a-zA-Z]"),
            Pattern.compile("[0-9]"),
            Pattern.compile("[!@#$%^*+=.\\-]")
    };

    private InputValidator() {}

    /**
     * 필수 문자열이 실제 문자를 포함하는지 검사한다.
     *
     * <p>앞뒤 공백을 제거하지 않으므로 검증을 통과한 원문이 그대로 반환된다.
     *
     * @param value 검증할 문자열
     * @param requiredMessage 값이 없을 때 사용할 예외 메시지
     * @return 검증을 통과한 원문
     * @throws IllegalArgumentException 값이 {@code null}, 빈 문자열 또는 공백 문자열인 경우
     */
    public static String requirePresent(String value, String requiredMessage) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(requiredMessage);
        }
        return value;
    }

    /**
     * 필수 문자열의 앞뒤 공백을 제거하고 최대 길이를 검사한다.
     *
     * @param value 검증할 문자열
     * @param maximumLength 허용할 최대 길이
     * @param requiredMessage 값이 없을 때 사용할 예외 메시지
     * @param lengthMessage 최대 길이를 초과할 때 사용할 예외 메시지
     * @return 앞뒤 공백이 제거된 문자열
     * @throws IllegalArgumentException 값이 없거나 정규화한 문자열이 최대 길이를 초과한 경우
     */
    public static String requireText(String value, int maximumLength, String requiredMessage, String lengthMessage) {
        requirePresent(value, requiredMessage);

        String normalized = value.trim();
        validateMaximumLength(normalized, maximumLength, lengthMessage);
        return normalized;
    }

    /**
     * 선택 문자열을 정규화하고 값이 있을 때만 최대 길이를 검사한다.
     *
     * @param value 검증할 문자열
     * @param maximumLength 허용할 최대 길이
     * @param lengthMessage 최대 길이를 초과할 때 사용할 예외 메시지
     * @return 값이 없으면 {@code null}, 값이 있으면 앞뒤 공백이 제거된 문자열
     * @throws IllegalArgumentException 정규화한 문자열이 최대 길이를 초과한 경우
     */
    public static String optionalText(String value, int maximumLength, String lengthMessage) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim();
        validateMaximumLength(normalized, maximumLength, lengthMessage);
        return normalized;
    }

    /**
     * 회원 아이디의 필수 여부, 길이와 허용 문자 형식을 검사한다.
     *
     * @param memberId 검증할 회원 아이디
     * @return 검증 실패 메시지, 검증을 통과하면 {@code null}
     */
    public static String memberIdError(String memberId) {
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

    /**
     * 비밀번호의 길이·허용 문자·문자군 조합과 비밀번호 확인값 일치를 검사한다.
     *
     * <p>비밀번호는 8~20자의 허용 문자로 구성되고 영문, 숫자, 특수문자 중 두 종류 이상을 포함해야 한다.
     *
     * @param password 검증할 비밀번호
     * @param passwordConfirm 비밀번호 확인값
     * @throws IllegalArgumentException 비밀번호 형식이 잘못되었거나 확인값이 일치하지 않는 경우
     */
    public static void validatePassword(String password, String passwordConfirm) {
        if (password == null || !PASSWORD_ALLOWED_PATTERN.matcher(password).matches()) {
            throw new IllegalArgumentException("비밀번호 형식을 확인해주세요.");
        }

        int groupCount = 0;
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

    /**
     * 선택 이메일의 최대 길이와 형식을 원문 기준으로 검사한다.
     *
     * <p>값이 없으면 검증을 생략하며 앞뒤 공백을 제거하지 않는다.
     *
     * @param email 검증할 이메일
     * @param formatMessage 길이 또는 형식이 잘못되었을 때 사용할 예외 메시지
     * @throws IllegalArgumentException 이메일이 255자를 초과하거나 형식이 잘못된 경우
     */
    public static void validateOptionalEmail(String email, String formatMessage) {
        if (email == null || email.isBlank()) {
            return;
        }
        if (email.length() > EMAIL_MAX_LENGTH || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException(formatMessage);
        }
    }

    /**
     * 선택 이메일의 앞뒤 공백을 제거한 뒤 최대 길이와 형식을 검사한다.
     *
     * @param email 검증할 이메일
     * @param lengthMessage 최대 길이를 초과할 때 사용할 예외 메시지
     * @param formatMessage 이메일 형식이 잘못되었을 때 사용할 예외 메시지
     * @return 값이 없으면 {@code null}, 값이 있으면 앞뒤 공백이 제거된 이메일
     * @throws IllegalArgumentException 이메일이 255자를 초과하거나 형식이 잘못된 경우
     */
    public static String normalizeOptionalEmail(String email, String lengthMessage, String formatMessage) {
        String normalized = optionalText(email, EMAIL_MAX_LENGTH, lengthMessage);
        if (normalized != null && !EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(formatMessage);
        }
        return normalized;
    }

    /**
     * 문자열이 허용된 최대 길이를 초과하는지 검사한다.
     *
     * @param value 검증할 문자열
     * @param maximumLength 허용할 최대 길이
     * @param lengthMessage 최대 길이를 초과할 때 사용할 예외 메시지
     * @throws IllegalArgumentException 문자열이 최대 길이를 초과한 경우
     */
    private static void validateMaximumLength(String value, int maximumLength, String lengthMessage) {
        if (value.length() > maximumLength) {
            throw new IllegalArgumentException(lengthMessage);
        }
    }
}
