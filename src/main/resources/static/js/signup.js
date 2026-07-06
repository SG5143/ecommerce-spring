document.addEventListener('DOMContentLoaded', function () {
    const usernameInput = document.getElementById('signup-username');
    const message = document.getElementById('signup-username-message');

    if (!usernameInput || !message) {
        return;
    }

    const DEBOUNCE_DELAY_MS = 400;
    let debounceTimer = null;

    function showMessage(text, passed) {
        message.textContent = text;
        message.classList.toggle('is-success', passed);
        message.classList.toggle('is-error', !passed);
    }

    function checkUsername() {
        const memberId = usernameInput.value.trim();

        fetch('/api/v1/members/check-id?member_id=' + encodeURIComponent(memberId))
            .then(function (response) {
                return response.json();
            })
            .then(function (data) {
                showMessage(data.msg, data.passed);
            })
            .catch(function () {
                showMessage('아이디 확인 중 오류가 발생했습니다.', false);
            });
    }

    usernameInput.addEventListener('input', function () {
        message.textContent = '';
        message.classList.remove('is-success', 'is-error');

        clearTimeout(debounceTimer);
        if (!usernameInput.value.trim()) {
            return;
        }
        debounceTimer = setTimeout(checkUsername, DEBOUNCE_DELAY_MS);
    });
});
