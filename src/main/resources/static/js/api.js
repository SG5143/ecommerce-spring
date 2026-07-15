/**
 * 공용 인증 유틸
 * - accessToken 은 localStorage 에 보관 (Refresh 토큰은 HttpOnly 쿠키라 JS 접근 불가)
 * - authFetch: 인증이 필요한 요청에 Bearer 헤더를 붙이고, 401 이면 자동으로 재발급 후 1회 재시도
 * 전역(window)에 노출해 다른 페이지에서도 재사용
 */
(function () {
    const ACCESS_TOKEN_KEY = 'accessToken';

    function getAccessToken() {
        return localStorage.getItem(ACCESS_TOKEN_KEY);
    }

    function setAccessToken(token) {
        if (token) {
            localStorage.setItem(ACCESS_TOKEN_KEY, token);
        }
    }

    function clearAccessToken() {
        localStorage.removeItem(ACCESS_TOKEN_KEY);
    }

    /**
     * Refresh 쿠키로 Access 토큰을 재발급
     * 성공 시 새 토큰을 저장하고 resolve, 실패 시 토큰을 제거하고 reject
     */
    function reissueAccessToken() {
        return fetch('/api/v1/auth/reissue', {
            method: 'POST'
        }).then(function (response) {
            if (!response.ok) {
                clearAccessToken();
                return Promise.reject(new Error('reissue failed'));
            }
            return response.json().then(function (data) {
                setAccessToken(data.accessToken);
                return data.accessToken;
            });
        });
    }

    /**
     * 인증이 필요한 API 호출 래퍼.
     * 1) Authorization 헤더를 붙여 요청
     * 2) 401 이면 재발급 후 원 요청을 1회 재시도 (무한루프 방지 플래그)
     * 3) 재발급 실패 시 토큰을 지우고 로그인 페이지로 이동
     */
    function authFetch(url, options) {
        return requestWithToken(url, options || {}, false);
    }

    function requestWithToken(url, options, retried) {
        const token = getAccessToken();
        const headers = Object.assign({}, options.headers || {});
        if (token) {
            headers['Authorization'] = 'Bearer ' + token;
        }

        const nextOptions = Object.assign({}, options, { headers: headers });

        return fetch(url, nextOptions).then(function (response) {
            if (response.status !== 401 || retried) {
                return response;
            }
            // 401 → 재발급 시도 후 1회 재시도
            return reissueAccessToken()
                .then(function () {
                    return requestWithToken(url, options, true);
                })
                .catch(function () {
                    clearAccessToken();
                    window.location.href = '/login';
                    return response;
                });
        });
    }

    window.getAccessToken = getAccessToken;
    window.setAccessToken = setAccessToken;
    window.clearAccessToken = clearAccessToken;
    window.reissueAccessToken = reissueAccessToken;
    window.authFetch = authFetch;
})();
