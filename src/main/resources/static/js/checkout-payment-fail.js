document.addEventListener('DOMContentLoaded', function () {
    const state = window.checkoutState;
    const pending = state.getPending();
    const message = document.getElementById('payment-fail-message');
    const retryLink = document.getElementById('payment-retry-link');
    const params = new URLSearchParams(window.location.search);
    const tossMessage = params.get('message');

    if (!pending || !pending.preparedPayment) {
        window.location.replace('/checkout/payment');
        return;
    }

    function headers() {
        const result = {};
        if (pending.ownerType === 'GUEST') {
            result['X-Guest-Order-Token'] = pending.guestOrderToken;
        }
        return result;
    }

    window.authFetch(
        '/api/v1/payments/preparations/'
            + encodeURIComponent(pending.preparedPayment.orderId)
            + '/cancel',
        { method: 'POST', headers: headers() })
        .then(function (response) {
            if (response.status === 409) {
                window.location.replace('/checkout/payment/success');
                return null;
            }
            if (!response.ok) {
                throw new Error('결제 준비 상태를 정리하지 못했습니다.');
            }
            return response.json();
        })
        .then(function (result) {
            if (!result) {
                return;
            }
            const next = state.renewPaymentAttempt(pending);
            state.updatePending(next, {
                notice: tossMessage || '결제 인증이 취소되었습니다. 다시 시도해주세요.'
            });
            message.textContent = tossMessage || '결제 인증이 취소되었습니다. 주문과 장바구니는 유지됩니다.';
            retryLink.hidden = false;
        })
        .catch(function (error) {
            message.textContent = error.message;
            retryLink.hidden = false;
        });
});
