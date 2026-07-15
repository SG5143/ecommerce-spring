/**
 * 전역 로그인 상태 UI 스크립트.
 * localStorage 의 accessToken 유무만으로 로그인 여부를 판단
 * - 헤더 마이 아이콘의 목적지를 로그인 시 /myshop 으로 전환
 * - 페이지 가드: 로그인 상태로 /login 진입 차단, 비로그인 상태로 /myshop 진입 차단
 * 의존성 없이 동작하도록 localStorage 를 직접 읽는다.
 */
document.addEventListener('DOMContentLoaded', function () {
    const loggedIn = !!localStorage.getItem('accessToken');
    const path = window.location.pathname;

    // 헤더 마이 아이콘: 로그인 상태면 마이샵으로
    const myLink = document.getElementById('header-my-link');
    if (myLink && loggedIn) {
        myLink.setAttribute('href', '/myshop');
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
});
