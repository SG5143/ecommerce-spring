package com.lsg.mingler.global.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InputValidatorTest {

    @Test
    void 필수값은_원문을_그대로_반환한다() {
        String value = "  mingler  ";

        String result = InputValidator.requirePresent(value, "필수값입니다.");

        assertThat(result).isEqualTo(value);
    }

    @Test
    void 필수값이_공백이면_지정한_메시지로_실패한다() {
        assertThatThrownBy(() -> InputValidator.requirePresent("   ", "필수값입니다."))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("필수값입니다.");
    }

    @Test
    void 필수_문자열은_공백을_제거한_뒤_최대길이를_검사한다() {
        String result = InputValidator.requireText(
                " 12345 ",
                5,
                "필수값입니다.",
                "5자 이하여야 합니다.");

        assertThat(result).isEqualTo("12345");
    }

    @Test
    void 필수_문자열이_최대길이를_넘으면_실패한다() {
        assertThatThrownBy(() -> InputValidator.requireText(
                "123456",
                5,
                "필수값입니다.",
                "5자 이하여야 합니다."))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("5자 이하여야 합니다.");
    }

    @Test
    void 선택_문자열이_공백이면_null을_반환한다() {
        String result = InputValidator.optionalText("   ", 5, "5자 이하여야 합니다.");

        assertThat(result).isNull();
    }

    @Test
    void 유효한_아이디면_오류가_없다() {
        assertThat(InputValidator.memberIdError("mingler_1")).isNull();
    }

    @Test
    void 아이디_길이와_정규식_오류를_구분한다() {
        assertThat(InputValidator.memberIdError("abc"))
                .isEqualTo("아이디는 4~16자여야 합니다.");
        assertThat(InputValidator.memberIdError("1mingler"))
                .isEqualTo("아이디는 소문자 영문으로 시작하는 소문자/숫자/.,_ 조합이어야 합니다.");
    }

    @Test
    void 두_종류_이상의_문자군을_사용한_비밀번호는_통과한다() {
        InputValidator.validatePassword("password1", "password1");
    }

    @Test
    void 한_종류의_문자군만_사용한_비밀번호는_실패한다() {
        assertThatThrownBy(() -> InputValidator.validatePassword("password", "password"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("비밀번호 형식을 확인해주세요.");
    }

    @Test
    void 비밀번호_확인이_다르면_실패한다() {
        assertThatThrownBy(() -> InputValidator.validatePassword("password1", "password2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("비밀번호가 일치하지 않습니다.");
    }

    @Test
    void 회원_이메일은_원문_공백을_정규화하지_않는다() {
        assertThatThrownBy(() -> InputValidator.validateOptionalEmail(
                " user@mingler.com ",
                "이메일 형식을 확인해주세요."))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이메일 형식을 확인해주세요.");
    }

    @Test
    void 주문_이메일은_공백을_제거해_반환한다() {
        String result = InputValidator.normalizeOptionalEmail(
                " user@mingler.com ",
                "이메일은 255자 이하여야 합니다.",
                "올바른 이메일 형식이 아닙니다.");

        assertThat(result).isEqualTo("user@mingler.com");
    }

    @Test
    void 이메일_형식이_틀리면_지정한_메시지로_실패한다() {
        assertThatThrownBy(() -> InputValidator.normalizeOptionalEmail(
                "invalid-email",
                "이메일은 255자 이하여야 합니다.",
                "올바른 이메일 형식이 아닙니다."))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("올바른 이메일 형식이 아닙니다.");
    }

    @Test
    void 주문_이메일이_최대길이를_넘으면_길이_메시지로_실패한다() {
        String email = "a".repeat(250) + "@test.com";

        assertThatThrownBy(() -> InputValidator.normalizeOptionalEmail(
                email,
                "이메일은 255자 이하여야 합니다.",
                "올바른 이메일 형식이 아닙니다."))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이메일은 255자 이하여야 합니다.");
    }
}
