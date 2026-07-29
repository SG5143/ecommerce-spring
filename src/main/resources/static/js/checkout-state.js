(function () {
    const SELECTION_KEY = 'mingler.checkout.selection';
    const PENDING_KEY = 'mingler.checkout.pending';
    const COMPLETE_KEY = 'mingler.checkout.complete';

    function read(key) {
        try {
            const value = JSON.parse(sessionStorage.getItem(key));
            return value && typeof value === 'object' ? value : null;
        } catch (error) {
            sessionStorage.removeItem(key);
            return null;
        }
    }

    function write(key, value) {
        sessionStorage.setItem(key, JSON.stringify(value));
        return value;
    }

    function normalizeItemIds(itemIds) {
        if (!Array.isArray(itemIds)) {
            return [];
        }
        return Array.from(new Set(itemIds.map(Number).filter(function (id) {
            return Number.isSafeInteger(id) && id > 0;
        })));
    }

    function getSelection() {
        const state = read(SELECTION_KEY);
        if (!state) {
            return null;
        }
        const cartItemIds = normalizeItemIds(state.cartItemIds);
        return cartItemIds.length > 0 ? { cartItemIds: cartItemIds } : null;
    }

    function setSelection(itemIds) {
        const cartItemIds = normalizeItemIds(itemIds);
        if (cartItemIds.length === 0) {
            sessionStorage.removeItem(SELECTION_KEY);
            return null;
        }
        return write(SELECTION_KEY, { cartItemIds: cartItemIds });
    }

    function createIdempotencyKey() {
        if (window.crypto && typeof window.crypto.randomUUID === 'function') {
            return window.crypto.randomUUID();
        }
        const bytes = new Uint8Array(16);
        window.crypto.getRandomValues(bytes);
        return Array.from(bytes).map(function (byte) {
            return byte.toString(16).padStart(2, '0');
        }).join('');
    }

    function getPending() {
        const state = read(PENDING_KEY);
        if (!state || (state.ownerType !== 'MEMBER' && state.ownerType !== 'GUEST')
            || !state.order || !state.order.orderNumber || !state.idempotencyKey) {
            sessionStorage.removeItem(PENDING_KEY);
            return null;
        }
        if (state.ownerType === 'GUEST' && !state.guestOrderToken) {
            sessionStorage.removeItem(PENDING_KEY);
            return null;
        }
        return state;
    }

    function setPending(orderResponse, ownerType) {
        if (!orderResponse || !orderResponse.orderNumber
            || (ownerType !== 'MEMBER' && ownerType !== 'GUEST')) {
            throw new Error('저장할 주문 정보가 올바르지 않습니다.');
        }
        const order = Object.assign({}, orderResponse);
        delete order.guestOrderToken;
        const state = {
            ownerType: ownerType,
            order: order,
            guestOrderToken: ownerType === 'GUEST' ? orderResponse.guestOrderToken : null,
            idempotencyKey: createIdempotencyKey()
        };
        sessionStorage.removeItem(COMPLETE_KEY);
        return write(PENDING_KEY, state);
    }

    function clearPending() {
        sessionStorage.removeItem(PENDING_KEY);
    }

    function renewPaymentAttempt(pending) {
        if (!pending || !pending.order || !pending.order.orderNumber) {
            throw new Error('갱신할 결제 정보가 올바르지 않습니다.');
        }
        const next = Object.assign({}, pending, {
            idempotencyKey: createIdempotencyKey()
        });
        return write(PENDING_KEY, next);
    }

    function getComplete() {
        const state = read(COMPLETE_KEY);
        if (!state || !state.order || !state.payment
            || !state.order.orderNumber || !state.payment.paymentNumber) {
            sessionStorage.removeItem(COMPLETE_KEY);
            return null;
        }
        return state;
    }

    function completePayment(pending, payment) {
        if (!pending || !pending.order || !payment) {
            throw new Error('완료할 결제 정보가 올바르지 않습니다.');
        }
        const state = {
            order: pending.order,
            payment: payment
        };
        sessionStorage.removeItem(SELECTION_KEY);
        sessionStorage.removeItem(PENDING_KEY);
        return write(COMPLETE_KEY, state);
    }

    function clearComplete() {
        sessionStorage.removeItem(COMPLETE_KEY);
    }

    window.checkoutState = {
        getSelection: getSelection,
        setSelection: setSelection,
        clearSelection: function () { sessionStorage.removeItem(SELECTION_KEY); },
        getPending: getPending,
        setPending: setPending,
        clearPending: clearPending,
        renewPaymentAttempt: renewPaymentAttempt,
        getComplete: getComplete,
        completePayment: completePayment,
        clearComplete: clearComplete
    };
})();
