document.addEventListener('DOMContentLoaded', function () {
    const badge = document.getElementById('header-cart-count');
    if (!badge || !window.authFetch) {
        return;
    }

    function renderCount(count) {
        const normalized = Number(count) || 0;
        badge.textContent = normalized > 99 ? '99+' : String(normalized);
        badge.hidden = normalized < 1;
    }

    function loadCount() {
        window.authFetch('/api/v1/cart/count')
            .then(function (response) {
                return response.ok ? response.json() : Promise.reject(new Error('count failed'));
            })
            .then(function (data) {
                renderCount(data.totalQuantity);
            })
            .catch(function () {
                renderCount(0);
            });
    }

    window.addEventListener('cart:updated', function (event) {
        if (event.detail && event.detail.totalQuantity !== undefined) {
            renderCount(event.detail.totalQuantity);
        } else {
            loadCount();
        }
    });
    loadCount();
});
