document.addEventListener('DOMContentLoaded', function () {
    loadSummary();

    const logoutButton = document.getElementById('myshop-logout');
    if (!logoutButton) {
        return;
    }

    logoutButton.addEventListener('click', function () {
        logoutButton.disabled = true;

        // Refresh 쿠키를 폐기하고, 성공 여부와 무관하게 클라이언트 토큰을 지운 뒤 홈으로 이동
        fetch('/api/v1/auth/logout', { method: 'POST' })
            .catch(function () {
                // 네트워크 오류여도 클라이언트 로그아웃은 진행
            })
            .then(function () {
                window.clearAccessToken();
                window.location.href = '/';
            });
    });

    /**
     * 인증 API로 마이샵 요약 정보를 불러와 화면에 채운다.
     * authFetch 가 401 시 재발급/재시도, 실패 시 /login 리다이렉트를 처리한다.
     */
    function loadSummary() {
        window.authFetch('/api/v1/members/me')
            .then(function (response) {
                if (!response.ok) {
                    return null;
                }
                return response.json();
            })
            .then(function (data) {
                if (!data) {
                    return;
                }
                setText('myshop-name', data.name);
                setText('myshop-grade', data.grade);
                setText('myshop-total-purchase', formatWon(data.totalPurchaseAmount));
                setText('myshop-points', formatWon(data.pointBalance));
                setText('myshop-coupons', data.couponCount.toLocaleString('ko-KR') + '장');
            })
            .catch(function () {
                // 네트워크 오류 시 자리표시자(-) 유지
            });
    }

    function setText(id, value) {
        const el = document.getElementById(id);
        if (el) {
            el.textContent = value;
        }
    }

    function formatWon(amount) {
        return Number(amount).toLocaleString('ko-KR') + '원';
    }
});
