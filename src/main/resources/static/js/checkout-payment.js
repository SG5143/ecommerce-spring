document.addEventListener('DOMContentLoaded', function () {
    const state = window.checkoutState;
    let pending = state.getPending();
    const content = document.getElementById('payment-content');
    const message = document.getElementById('payment-message');
    const confirmButton = document.getElementById('payment-confirm');
    const providerInputs = document.querySelectorAll('input[name="paymentProvider"]');

    if (!pending) {
        if (state.getSelection()) {
            window.location.replace('/checkout');
        } else {
            sessionStorage.setItem('cartNotice', '결제할 주문 정보가 없습니다. 상품을 다시 선택해주세요.');
            window.location.replace('/cart');
        }
        return;
    }
    if (pending.approvalCallback) {
        window.location.replace('/checkout/payment/success');
        return;
    }

    function formatWon(amount) {
        return '₩' + Number(amount || 0).toLocaleString('ko-KR');
    }

    function showMessage(text) {
        message.textContent = text || '';
        if (text) {
            message.scrollIntoView({ behavior: 'smooth', block: 'center' });
        }
    }

    function appendText(parent, tagName, className, text) {
        const element = document.createElement(tagName);
        element.className = className;
        element.textContent = text;
        parent.appendChild(element);
        return element;
    }

    function renderItems(items) {
        const list = document.getElementById('payment-order-items');
        list.textContent = '';
        (items || []).forEach(function (item) {
            const row = document.createElement('li');
            row.className = 'checkout-order-item';
            const image = document.createElement(item.thumbnailUrl ? 'img' : 'span');
            image.className = 'checkout-order-image';
            if (item.thumbnailUrl) {
                image.src = item.thumbnailUrl;
                image.alt = '';
            }
            row.appendChild(image);
            const info = document.createElement('div');
            info.className = 'checkout-order-info';
            appendText(info, 'strong', '', item.productName);
            if (item.optionName) {
                appendText(info, 'span', '', '옵션: ' + item.optionName);
            }
            appendText(info, 'span', '', '수량: ' + item.quantity + ' · 단가 ' + formatWon(item.unitPrice));
            row.appendChild(info);
            appendText(row, 'strong', 'checkout-order-price', formatWon(item.lineAmount));
            list.appendChild(row);
        });
    }

    function requestJson(url, options) {
        return window.authFetch(url, options || {}).then(function (response) {
            return response.json().catch(function () { return {}; }).then(function (data) {
                if (!response.ok) {
                    const error = new Error(data.message || '결제 요청을 처리하지 못했습니다.');
                    error.status = response.status;
                    throw error;
                }
                data.httpStatus = response.status;
                return data;
            });
        });
    }

    function ownershipHeaders() {
        const result = {};
        if (pending.ownerType === 'GUEST') {
            result['X-Guest-Order-Token'] = pending.guestOrderToken;
        }
        return result;
    }

    function headers() {
        return Object.assign({
            'Content-Type': 'application/json',
            'Idempotency-Key': pending.idempotencyKey
        }, ownershipHeaders());
    }

    function setPaymentControlsDisabled(disabled) {
        confirmButton.disabled = disabled;
        providerInputs.forEach(function (input) {
            input.disabled = disabled;
        });
    }

    function createVirtualPaymentKey(orderId) {
        if (window.crypto && typeof window.crypto.randomUUID === 'function') {
            return 'VIRTUAL-' + window.crypto.randomUUID();
        }
        return 'VIRTUAL-' + orderId;
    }

    function selectedPaymentProvider() {
        const selected = document.querySelector('input[name="paymentProvider"]:checked');
        return selected ? selected.value : null;
    }

    function resetPreparationIfProviderChanged(paymentProvider) {
        const prepared = pending.preparedPayment;
        if (!prepared || prepared.paymentProvider === paymentProvider) {
            return Promise.resolve(true);
        }

        confirmButton.textContent = '기존 결제 준비를 정리하는 중...';
        return requestJson(
            '/api/v1/payments/preparations/' + encodeURIComponent(prepared.orderId) + '/cancel',
            {
                method: 'POST',
                headers: ownershipHeaders()
            })
            .then(function () {
                pending = state.renewPaymentAttempt(pending);
                return true;
            })
            .catch(function (error) {
                if (error.status === 404) {
                    pending = state.renewPaymentAttempt(pending);
                    return true;
                }
                if (error.status === 409) {
                    window.location.replace('/checkout/payment/success');
                    return false;
                }
                throw error;
            });
    }

    function preparePayment(paymentProvider) {
        confirmButton.textContent = '결제를 준비하는 중...';
        return requestJson('/api/v1/payments/prepare', {
            method: 'POST',
            headers: headers(),
            body: JSON.stringify({
                orderNumber: order.orderNumber,
                paymentMethod: 'CARD',
                paymentProvider: paymentProvider
            })
        });
    }

    function confirmVirtual(prepared) {
        const callback = {
            paymentKey: createVirtualPaymentKey(prepared.orderId),
            orderId: prepared.orderId,
            amount: prepared.amount
        };
        pending = state.setApprovalCallback(pending, callback);
        return requestJson('/api/v1/payments/confirm', {
            method: 'POST',
            headers: headers(),
            body: JSON.stringify(callback)
        }).then(function (paymentResult) {
            if (paymentResult.paymentStatus === 'SUCCESS') {
                state.completePayment(pending, paymentResult);
                window.location.href = '/checkout/complete';
                return;
            }
            window.location.href = '/checkout/payment/success';
        });
    }

    function openTossWindow(prepared) {
        if (typeof window.TossPayments !== 'function') {
            throw new Error('토스페이먼츠 결제창을 불러오지 못했습니다.');
        }
        const tossPayments = window.TossPayments(prepared.clientKey);
        const payment = tossPayments.payment({ customerKey: prepared.customerKey });
        return payment.requestPayment({
            method: 'CARD',
            amount: { value: prepared.amount, currency: 'KRW' },
            orderId: prepared.orderId,
            orderName: prepared.orderName,
            successUrl: location.origin + '/checkout/payment/success',
            failUrl: location.origin + '/checkout/payment/fail'
        });
    }

    const order = pending.order;
    document.getElementById('payment-order-number').textContent = order.orderNumber;
    document.getElementById('payment-merchandise-amount').textContent = formatWon(order.merchandiseAmount);
    document.getElementById('payment-discount-amount').textContent = '-₩'
        + Number(order.discountAmount || 0).toLocaleString('ko-KR');
    document.getElementById('payment-shipping-fee').textContent = formatWon(order.shippingFee);
    document.getElementById('payment-total-amount').textContent = formatWon(order.totalAmount);
    renderItems(order.items);
    if (pending.notice) {
        showMessage(pending.notice);
        pending = state.updatePending(pending, { notice: null });
    }
    content.hidden = false;

    confirmButton.addEventListener('click', function () {
        if (pending.ownerType === 'GUEST' && !pending.guestOrderToken) {
            state.clearPending();
            showMessage('비회원 주문 확인 정보가 없어 결제를 계속할 수 없습니다.');
            return;
        }
        if (pending.approvalCallback) {
            window.location.replace('/checkout/payment/success');
            return;
        }

        const paymentProvider = selectedPaymentProvider();
        if (!paymentProvider) {
            showMessage('결제수단을 선택해주세요.');
            return;
        }

        setPaymentControlsDisabled(true);
        showMessage('');
        resetPreparationIfProviderChanged(paymentProvider)
            .then(function (canPrepare) {
                return canPrepare ? preparePayment(paymentProvider) : null;
            })
            .then(function (prepared) {
                if (!prepared) {
                    return;
                }
                pending = state.setPreparedPayment(pending, prepared);
                confirmButton.textContent = '결제를 처리하는 중...';
                return prepared.paymentProvider === 'TOSS'
                    ? openTossWindow(prepared)
                    : confirmVirtual(prepared);
            })
            .catch(function (error) {
                setPaymentControlsDisabled(false);
                if (error.status === 402) {
                    pending = state.renewPaymentAttempt(pending);
                    confirmButton.textContent = '새 요청으로 다시 결제하기';
                } else {
                    confirmButton.textContent = '결제하기';
                }
                if (error.status === 404) {
                    state.clearPending();
                    state.clearSelection();
                    sessionStorage.setItem('cartNotice', error.message);
                    window.location.replace('/cart');
                    return;
                }
                showMessage(error.status
                    ? error.message
                    : (error.message || '네트워크 오류가 발생했습니다. 같은 요청으로 다시 시도해주세요.'));
            });
    });
});
