document.addEventListener('DOMContentLoaded', function () {
    const state = window.checkoutState;
    let pending = state.getPending();
    const message = document.getElementById('payment-success-message');
    const retryButton = document.getElementById('payment-status-retry');
    let pollCount = 0;
    let polling = false;

    if (!pending || !pending.preparedPayment) {
        window.location.replace('/checkout/payment');
        return;
    }

    function headers(includeContentType) {
        const result = {};
        if (includeContentType) {
            result['Content-Type'] = 'application/json';
            result['Idempotency-Key'] = pending.idempotencyKey;
        }
        if (pending.ownerType === 'GUEST') {
            result['X-Guest-Order-Token'] = pending.guestOrderToken;
        }
        return result;
    }

    function requestJson(url, options) {
        return window.authFetch(url, options || {}).then(function (response) {
            return response.json().catch(function () { return {}; }).then(function (data) {
                if (!response.ok) {
                    const error = new Error(data.message || '결제 결과를 확인하지 못했습니다.');
                    error.status = response.status;
                    throw error;
                }
                data.httpStatus = response.status;
                return data;
            });
        });
    }

    function finish(payment) {
        state.completePayment(pending, payment);
        window.location.replace('/checkout/complete');
    }

    function returnForRetry(payment) {
        const next = state.renewPaymentAttempt(pending);
        state.updatePending(next, {
            notice: payment.failureReason || '결제가 완료되지 않았습니다. 다시 시도해주세요.'
        });
        window.location.replace('/checkout/payment');
    }

    function handleStatus(payment) {
        if (payment.paymentStatus === 'SUCCESS') {
            finish(payment);
            return;
        }
        if (payment.paymentStatus === 'FAILED' || payment.paymentStatus === 'CANCELLED') {
            returnForRetry(payment);
            return;
        }
        if (pollCount >= 15) {
            polling = false;
            message.textContent = '결제 승인 결과를 계속 확인하고 있습니다. 잠시 후 다시 확인해주세요.';
            retryButton.hidden = false;
            return;
        }
        pollCount += 1;
        window.setTimeout(pollStatus, 2000);
    }

    function pollStatus() {
        if (!polling) {
            polling = true;
        }
        requestJson(
            '/api/v1/payments/attempts/' + encodeURIComponent(pending.preparedPayment.orderId),
            { headers: headers(false) })
            .then(handleStatus)
            .catch(function () {
                if (pollCount >= 15) {
                    polling = false;
                    message.textContent = '네트워크 문제로 결제 결과를 확인하지 못했습니다.';
                    retryButton.hidden = false;
                    return;
                }
                pollCount += 1;
                window.setTimeout(pollStatus, 2000);
            });
    }

    function readCallback() {
        const params = new URLSearchParams(window.location.search);
        if (!params.has('paymentKey')) {
            return pending.approvalCallback || null;
        }
        const callback = {
            paymentKey: params.get('paymentKey'),
            orderId: params.get('orderId'),
            amount: Number(params.get('amount'))
        };
        if (callback.orderId !== pending.preparedPayment.orderId
            || callback.amount !== Number(pending.preparedPayment.amount)
            || !callback.paymentKey) {
            window.location.replace(
                '/checkout/payment/fail?message='
                + encodeURIComponent('결제 인증 정보가 준비한 주문과 일치하지 않습니다.'));
            return null;
        }
        pending = state.setApprovalCallback(pending, callback);
        return callback;
    }

    function confirm(callback) {
        requestJson('/api/v1/payments/confirm', {
            method: 'POST',
            headers: headers(true),
            body: JSON.stringify(callback)
        })
            .then(handleStatus)
            .catch(function (error) {
                if (error.status === 402) {
                    const next = state.renewPaymentAttempt(pending);
                    state.updatePending(next, { notice: error.message });
                    window.location.replace('/checkout/payment');
                    return;
                }
                message.textContent = '승인 응답을 확인하지 못해 결제 상태를 조회하고 있습니다.';
                pollStatus();
            });
    }

    retryButton.addEventListener('click', function () {
        retryButton.hidden = true;
        message.textContent = '결제 결과를 다시 확인하고 있습니다.';
        pollCount = 0;
        polling = true;
        pollStatus();
    });

    const callback = readCallback();
    if (callback) {
        confirm(callback);
    } else {
        pollStatus();
    }
});
