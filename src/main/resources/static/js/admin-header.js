document.addEventListener('DOMContentLoaded', function () {
    const logoutButton = document.getElementById('admin-logout');
    if (!logoutButton) {
        return;
    }

    logoutButton.addEventListener('click', function () {
        logoutButton.disabled = true;

        fetch('/api/v1/auth/logout', { method: 'POST' })
            .catch(function () {
                // 서버 요청 실패 여부와 관계없이 브라우저의 Access Token은 정리한다.
            })
            .then(function () {
                if (typeof window.clearAccessToken === 'function') {
                    window.clearAccessToken();
                } else {
                    localStorage.removeItem('accessToken');
                }
                window.location.href = '/login';
            });
    });
});
