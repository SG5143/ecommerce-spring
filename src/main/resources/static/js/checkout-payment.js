document.addEventListener('DOMContentLoaded', function () {
    const state = window.checkoutState;
    const pending = state.getPending();
    const content = document.getElementById('payment-content');
    const message = document.getElementById('payment-message');
    const confirmButton = document.getElementById('payment-confirm');

    if (!pending) {
        if (state.getSelection()) {
            window.location.replace('/checkout');
        } else {
            sessionStorage.setItem('cartNotice', '결제할 주문 정보가 없습니다. 상품을 다시 선택해주세요.');
            window.location.replace('/cart');
        }
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
                return data;
            });
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
    content.hidden = false;

    confirmButton.addEventListener('click', function () {
        if (pending.ownerType === 'GUEST' && !pending.guestOrderToken) {
            state.clearPending();
            showMessage('비회원 주문 확인 정보가 없어 결제를 계속할 수 없습니다.');
            return;
        }

        const headers = {
            'Content-Type': 'application/json',
            'Idempotency-Key': pending.idempotencyKey
        };
        if (pending.ownerType === 'GUEST') {
            headers['X-Guest-Order-Token'] = pending.guestOrderToken;
        }

        confirmButton.disabled = true;
        confirmButton.textContent = '결제를 처리하는 중...';
        showMessage('');
        requestJson('/api/v1/payments/confirm', {
            method: 'POST',
            headers: headers,
            body: JSON.stringify({
                orderNumber: order.orderNumber,
                amount: order.totalAmount,
                paymentMethod: 'CARD'
            })
        })
            .then(function (payment) {
                state.completePayment(pending, payment);
                window.location.href = '/checkout/complete';
            })
            .catch(function (error) {
                confirmButton.disabled = false;
                confirmButton.textContent = '같은 요청으로 다시 결제하기';
                if (error.status === 404) {
                    state.clearPending();
                    state.clearSelection();
                    sessionStorage.setItem('cartNotice', error.message);
                    window.location.replace('/cart');
                    return;
                }
                showMessage(error.status
                    ? error.message
                    : '네트워크 오류가 발생했습니다. 같은 요청으로 다시 시도해주세요.');
            });
    });
});
