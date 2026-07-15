document.addEventListener('DOMContentLoaded', function () {
    const form = document.querySelector('.login-form');
    if (!form) {
        return;
    }

    const usernameInput = form.querySelector('input[name="username"]');
    const passwordInput = form.querySelector('input[name="password"]');
    const submitButton = form.querySelector('.login-submit');
    const messageEl = form.querySelector('.login-message');

    function showMessage(text) {
        if (messageEl) {
            messageEl.textContent = text;
            messageEl.classList.add('is-error');
        }
    }

    function clearMessage() {
        if (messageEl) {
            messageEl.textContent = '';
            messageEl.classList.remove('is-error');
        }
    }

    form.addEventListener('submit', function (event) {
        event.preventDefault();
        clearMessage();

        const memberId = usernameInput ? usernameInput.value.trim() : '';
        const password = passwordInput ? passwordInput.value : '';

        if (!memberId || !password) {
            showMessage('아이디와 비밀번호를 모두 입력해주세요.');
            return;
        }

        if (submitButton) {
            submitButton.disabled = true;
        }

        fetch('/api/v1/auth/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ memberId: memberId, password: password })
        })
            .then(function (response) {
                return response.json().catch(function () {
                    return {};
                }).then(function (data) {
                    return { ok: response.ok, data: data };
                });
            })
            .then(function (result) {
                if (result.ok) {
                    window.setAccessToken(result.data.accessToken);
                    window.location.href = '/';
                    return;
                }
                if (submitButton) {
                    submitButton.disabled = false;
                }
                const message = result.data && result.data.message
                    ? result.data.message
                    : '로그인에 실패했습니다. 입력 정보를 확인해주세요.';
                showMessage(message);
            })
            .catch(function () {
                if (submitButton) {
                    submitButton.disabled = false;
                }
                showMessage('로그인 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.');
            });
    });
});
