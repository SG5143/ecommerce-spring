document.addEventListener('DOMContentLoaded', function () {
    function showMessage(el, text, passed) {
        el.textContent = text;
        el.classList.toggle('is-success', passed);
        el.classList.toggle('is-error', !passed);
    }

    function clearMessage(el) {
        el.textContent = '';
        el.classList.remove('is-success', 'is-error');
    }

    const usernameInput = document.getElementById('signup-username');
    const usernameMessage = document.getElementById('signup-username-message');

    if (usernameInput && usernameMessage) {
        const DEBOUNCE_DELAY_MS = 400;
        let debounceTimer = null;

        function checkUsername() {
            const memberId = usernameInput.value.trim();

            fetch('/api/v1/members/check-id?member_id=' + encodeURIComponent(memberId))
                .then(function (response) {
                    return response.json();
                })
                .then(function (data) {
                    showMessage(usernameMessage, data.msg, data.passed);
                })
                .catch(function () {
                    showMessage(usernameMessage, '아이디 확인 중 오류가 발생했습니다.', false);
                });
        }

        usernameInput.addEventListener('input', function () {
            clearMessage(usernameMessage);

            clearTimeout(debounceTimer);
            if (!usernameInput.value.trim()) {
                return;
            }
            debounceTimer = setTimeout(checkUsername, DEBOUNCE_DELAY_MS);
        });
    }

    const passwordInput = document.getElementById('signup-password');
    const passwordMessage = document.getElementById('signup-password-message');
    const passwordConfirmInput = document.getElementById('signup-password-confirm');
    const passwordConfirmMessage = document.getElementById('signup-password-confirm-message');
    const PASSWORD_ALLOWED_PATTERN = /^[a-zA-Z0-9!@#$%^*+=.-]{8,20}$/;
    const PASSWORD_CHAR_GROUPS = [/[a-zA-Z]/, /[0-9]/, /[!@#$%^*+=.-]/];

    function isPasswordValid(value) {
        if (!PASSWORD_ALLOWED_PATTERN.test(value)) {
            return false;
        }
        const groupCount = PASSWORD_CHAR_GROUPS.filter(function (group) {
            return group.test(value);
        }).length;
        return groupCount >= 2;
    }

    function checkPasswordMatch() {
        if (!passwordConfirmInput.value) {
            clearMessage(passwordConfirmMessage);
            return;
        }

        if (passwordInput.value === passwordConfirmInput.value) {
            clearMessage(passwordConfirmMessage);
        } else {
            showMessage(passwordConfirmMessage, '비밀번호가 일치하지 않습니다.', false);
        }
    }

    if (passwordInput && passwordMessage) {
        passwordInput.addEventListener('input', function () {
            if (!passwordInput.value) {
                clearMessage(passwordMessage);
            } else if (isPasswordValid(passwordInput.value)) {
                clearMessage(passwordMessage);
            } else {
                showMessage(passwordMessage, '영문, 숫자, 특수문자(!@#$%^*+=.-) 중 2가지 이상을 조합한 8~20자여야 합니다.', false);
            }

            if (passwordConfirmInput && passwordConfirmMessage) {
                checkPasswordMatch();
            }
        });
    }

    if (passwordConfirmInput && passwordConfirmMessage && passwordInput) {
        passwordConfirmInput.addEventListener('input', checkPasswordMatch);
    }
});
