/**
 * 전역 로그인 상태 UI 스크립트.
 * localStorage 의 accessToken 유무로 로그인 여부를 판단하고 JWT role 클레임으로 관리자 아이콘을 제어
 * - 헤더 마이 아이콘의 목적지를 로그인 시 /myshop 으로 전환
 * - ADMIN 토큰일 때만 관리자 아이콘 노출
 * - 페이지 가드: 로그인 상태로 /login 진입 차단, 비로그인 상태로 /myshop 진입 차단
 * 실제 관리자 접근 권한은 서버의 ROLE_ADMIN 인가 규칙이 최종 검증한다.
 */
document.addEventListener('DOMContentLoaded', function () {
    const accessToken = localStorage.getItem('accessToken');
    const loggedIn = !!accessToken;
    const path = window.location.pathname;

    // 헤더 마이 아이콘: 로그인 상태면 마이샵으로
    const myLink = document.getElementById('header-my-link');
    if (myLink && loggedIn) {
        myLink.setAttribute('href', '/myshop');
    }

    const adminLink = document.getElementById('header-admin-link');
    const claims = parseJwtClaims(accessToken);
    if (adminLink && claims && claims.role === 'ADMIN') {
        adminLink.hidden = false;
        adminLink.addEventListener('click', function (event) {
            if (!isExpired(claims)) {
                return;
            }

            event.preventDefault();
            if (typeof window.reissueAccessToken !== 'function') {
                window.location.href = '/login';
                return;
            }

            const adminWindow = window.open('about:blank', '_blank');
            if (adminWindow) {
                adminWindow.opener = null;
            }

            window.reissueAccessToken()
                .then(function (refreshedToken) {
                    const refreshedClaims = parseJwtClaims(refreshedToken);
                    if (refreshedClaims && refreshedClaims.role === 'ADMIN') {
                        if (adminWindow) {
                            adminWindow.location.replace('/admin');
                        } else {
                            window.location.href = '/admin';
                        }
                        return;
                    }
                    if (adminWindow) {
                        adminWindow.close();
                    }
                    adminLink.hidden = true;
                    window.location.href = '/';
                })
                .catch(function () {
                    if (adminWindow) {
                        adminWindow.close();
                    }
                    window.location.href = '/login';
                });
        });
    }

    // 로그인 상태로 로그인 페이지 진입 → 마이샵으로
    if (loggedIn && path === '/login') {
        window.location.replace('/myshop');
        return;
    }

    // 비로그인 상태로 마이샵 진입 → 로그인 페이지로
    if (!loggedIn && path.indexOf('/myshop') === 0) {
        window.location.replace('/login');
    }

    function parseJwtClaims(token) {
        if (!token) {
            return null;
        }
        try {
            const segments = token.split('.');
            if (segments.length !== 3) {
                return null;
            }
            const base64 = segments[1].replace(/-/g, '+').replace(/_/g, '/');
            const padded = base64.padEnd(Math.ceil(base64.length / 4) * 4, '=');
            return JSON.parse(window.atob(padded));
        } catch (error) {
            return null;
        }
    }

    function isExpired(tokenClaims) {
        return typeof tokenClaims.exp === 'number' && tokenClaims.exp * 1000 <= Date.now();
    }
});
