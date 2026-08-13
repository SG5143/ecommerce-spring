document.addEventListener('DOMContentLoaded', function () {
    const state = window.checkoutState;
    const complete = state.getComplete();
    const content = document.getElementById('checkout-complete-content');

    if (!complete) {
        if (state.getPending()) {
            window.location.replace('/checkout/payment');
        } else if (state.getSelection()) {
            window.location.replace('/checkout');
        } else {
            window.location.replace('/');
        }
        return;
    }

    function formatWon(amount) {
        return '₩' + Number(amount || 0).toLocaleString('ko-KR');
    }

    function formatDateTime(value) {
        if (!value) {
            return '-';
        }
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) {
            return value.replace('T', ' ');
        }
        return new Intl.DateTimeFormat('ko-KR', {
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit'
        }).format(date);
    }

    function setText(id, value) {
        document.getElementById(id).textContent = value == null || value === '' ? '-' : String(value);
    }

    function appendText(parent, tagName, className, text) {
        const element = document.createElement(tagName);
        element.className = className;
        element.textContent = text;
        parent.appendChild(element);
        return element;
    }

    const order = complete.order;
    const payment = complete.payment;
    const orderHistoryLink = document.getElementById('complete-order-history-link');
    if (orderHistoryLink && window.getAccessToken()) {
        orderHistoryLink.hidden = false;
    }
    setText('complete-order-number', payment.orderNumber || order.orderNumber);
    setText('complete-payment-number', payment.paymentNumber);
    setText('complete-payment-status', payment.paymentStatus);
    setText('complete-order-status', payment.orderStatus);
    setText('complete-payment-method', payment.paymentMethod);
    setText('complete-approved-at', formatDateTime(payment.approvedAt));
    setText('complete-pg-key', payment.pgTransactionKey);
    setText('complete-amount', formatWon(payment.amount));

    const list = document.getElementById('complete-order-items');
    (order.items || []).forEach(function (item) {
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
        appendText(info, 'span', '', '수량: ' + item.quantity);
        row.appendChild(info);
        appendText(row, 'strong', 'checkout-order-price', formatWon(item.lineAmount));
        list.appendChild(row);
    });

    document.querySelectorAll('[data-complete-leave]').forEach(function (link) {
        link.addEventListener('click', function () {
            state.clearComplete();
        });
    });
    content.hidden = false;
});
