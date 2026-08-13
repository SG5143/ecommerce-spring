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

    function normalizeDirectItems(items) {
        if (!Array.isArray(items)) {
            return [];
        }
        const variants = new Set();
        const normalized = [];
        for (const item of items) {
            const productId = Number(item && item.productId);
            const optionId = Number(item && item.optionId);
            const quantity = Number(item && item.quantity);
            const unitPrice = Number(item && item.unitPrice);
            const variantKey = productId + ':' + optionId;
            if (!Number.isSafeInteger(productId) || productId <= 0
                || !Number.isSafeInteger(optionId) || optionId <= 0
                || !Number.isSafeInteger(quantity) || quantity < 1 || quantity > 99
                || !Number.isSafeInteger(unitPrice) || unitPrice < 0
                || variants.has(variantKey)) {
                return [];
            }
            variants.add(variantKey);
            normalized.push({
                productId: productId,
                optionId: optionId,
                quantity: quantity,
                productName: String(item.productName || ''),
                optionName: item.optionName ? String(item.optionName) : null,
                thumbnailUrl: item.thumbnailUrl ? String(item.thumbnailUrl) : null,
                unitPrice: unitPrice,
                lineTotal: unitPrice * quantity,
                available: true
            });
        }
        return normalized;
    }

    function normalizeProductReturnUrl(returnUrl) {
        return typeof returnUrl === 'string' && /^\/products\/\d+$/.test(returnUrl)
            ? returnUrl
            : '/';
    }

    function getSelection() {
        const state = read(SELECTION_KEY);
        if (!state) {
            return null;
        }
        if (state.source === 'DIRECT') {
            const directItems = normalizeDirectItems(state.directItems);
            return directItems.length > 0 ? {
                source: 'DIRECT',
                directItems: directItems,
                returnUrl: normalizeProductReturnUrl(state.returnUrl)
            } : null;
        }
        const cartItemIds = normalizeItemIds(state.cartItemIds);
        return cartItemIds.length > 0 ? {
            source: 'CART',
            cartItemIds: cartItemIds,
            returnUrl: '/cart'
        } : null;
    }

    function setSelection(itemIds) {
        const cartItemIds = normalizeItemIds(itemIds);
        if (cartItemIds.length === 0) {
            sessionStorage.removeItem(SELECTION_KEY);
            return null;
        }
        return write(SELECTION_KEY, { source: 'CART', cartItemIds: cartItemIds, returnUrl: '/cart' });
    }

    function setDirectSelection(items, returnUrl) {
        const directItems = normalizeDirectItems(items);
        if (directItems.length === 0) {
            sessionStorage.removeItem(SELECTION_KEY);
            return null;
        }
        return write(SELECTION_KEY, {
            source: 'DIRECT',
            directItems: directItems,
            returnUrl: normalizeProductReturnUrl(returnUrl)
        });
    }

    function getReturnUrl(state) {
        return state && state.source === 'DIRECT'
            ? normalizeProductReturnUrl(state.returnUrl)
            : '/cart';
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
        const selection = getSelection();
        const state = {
            ownerType: ownerType,
            order: order,
            guestOrderToken: ownerType === 'GUEST' ? orderResponse.guestOrderToken : null,
            idempotencyKey: createIdempotencyKey(),
            source: selection ? selection.source : 'CART',
            returnUrl: getReturnUrl(selection)
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
            idempotencyKey: createIdempotencyKey(),
            preparedPayment: null,
            approvalCallback: null
        });
        return write(PENDING_KEY, next);
    }

    function updatePending(pending, changes) {
        if (!pending || !pending.order || !pending.order.orderNumber) {
            throw new Error('갱신할 결제 정보가 올바르지 않습니다.');
        }
        return write(PENDING_KEY, Object.assign({}, pending, changes || {}));
    }

    function setPreparedPayment(pending, preparedPayment) {
        return updatePending(pending, { preparedPayment: preparedPayment });
    }

    function setApprovalCallback(pending, approvalCallback) {
        return updatePending(pending, { approvalCallback: approvalCallback });
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
        setCartSelection: setSelection,
        setDirectSelection: setDirectSelection,
        getReturnUrl: getReturnUrl,
        clearSelection: function () { sessionStorage.removeItem(SELECTION_KEY); },
        getPending: getPending,
        setPending: setPending,
        clearPending: clearPending,
        renewPaymentAttempt: renewPaymentAttempt,
        setPreparedPayment: setPreparedPayment,
        setApprovalCallback: setApprovalCallback,
        updatePending: updatePending,
        getComplete: getComplete,
        completePayment: completePayment,
        clearComplete: clearComplete
    };
})();
