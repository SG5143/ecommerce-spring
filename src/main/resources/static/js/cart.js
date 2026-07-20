document.addEventListener('DOMContentLoaded', function () {
    const loading = document.getElementById('cart-loading');
    const content = document.getElementById('cart-content');
    const empty = document.getElementById('cart-empty');
    const list = document.getElementById('cart-items');
    const selectAll = document.getElementById('cart-select-all');
    const deleteSelected = document.getElementById('cart-delete-selected');
    const selectedTotal = document.getElementById('cart-selected-total');
    const message = document.getElementById('cart-page-message');
    let cart = { items: [], totalQuantity: 0 };
    const selectedIds = new Set();

    function formatWon(amount) {
        return '₩' + Number(amount).toLocaleString('ko-KR');
    }

    function showMessage(text, isError) {
        message.textContent = text || '';
        message.classList.toggle('is-error', !!isError);
    }

    function updateSelectionSummary() {
        let total = 0;
        cart.items.forEach(function (item) {
            if (selectedIds.has(item.id) && item.available) {
                total += item.lineTotal;
            }
        });
        selectedTotal.textContent = formatWon(total);
        const availableIds = cart.items.filter(function (item) { return item.available; })
            .map(function (item) { return item.id; });
        selectAll.checked = availableIds.length > 0 && availableIds.every(function (id) {
            return selectedIds.has(id);
        });
        selectAll.indeterminate = !selectAll.checked && availableIds.some(function (id) {
            return selectedIds.has(id);
        });
    }

    function appendTextElement(parent, tagName, className, text) {
        const element = document.createElement(tagName);
        element.className = className;
        element.textContent = text;
        parent.appendChild(element);
        return element;
    }

    function createItem(item) {
        const row = document.createElement('li');
        row.className = 'cart-item' + (item.available ? '' : ' is-unavailable');
        row.dataset.itemId = item.id;

        const checkbox = document.createElement('input');
        checkbox.type = 'checkbox';
        checkbox.className = 'cart-item-check';
        checkbox.checked = selectedIds.has(item.id);
        checkbox.disabled = !item.available;
        checkbox.setAttribute('aria-label', item.productName + ' 선택');
        row.appendChild(checkbox);

        const imageLink = document.createElement('a');
        imageLink.href = '/products/' + item.productId;
        imageLink.className = 'cart-item-image';
        if (item.thumbnailUrl) {
            const image = document.createElement('img');
            image.src = item.thumbnailUrl;
            image.alt = item.productName;
            imageLink.appendChild(image);
        }
        row.appendChild(imageLink);

        const info = document.createElement('div');
        info.className = 'cart-item-info';
        const nameLink = appendTextElement(info, 'a', 'cart-item-name', item.productName);
        nameLink.href = '/products/' + item.productId;
        if (item.optionName) {
            appendTextElement(info, 'p', 'cart-item-option', '옵션: ' + item.optionName);
        }
        if (item.priceChanged) {
            appendTextElement(info, 'p', 'cart-item-notice', '가격이 ' + formatWon(item.currentUnitPrice) + '으로 변경되었습니다.');
        }
        if (!item.available) {
            appendTextElement(info, 'p', 'cart-item-error', item.unavailableReason);
        }
        row.appendChild(info);

        const stepper = document.createElement('div');
        stepper.className = 'qty-stepper cart-item-stepper';
        const minus = appendTextElement(stepper, 'button', 'qty-btn cart-qty-minus', '−');
        minus.type = 'button';
        minus.disabled = !item.available || item.quantity <= 1;
        appendTextElement(stepper, 'span', 'qty-value', item.quantity);
        const plus = appendTextElement(stepper, 'button', 'qty-btn cart-qty-plus', '+');
        plus.type = 'button';
        plus.disabled = !item.available || item.quantity >= Math.min(item.stockQuantity, 99);
        row.appendChild(stepper);

        appendTextElement(row, 'strong', 'cart-item-price', formatWon(item.lineTotal));
        const remove = appendTextElement(row, 'button', 'cart-item-remove', '삭제');
        remove.type = 'button';
        return row;
    }

    function render(nextCart, resetSelection) {
        cart = nextCart;
        if (resetSelection) {
            selectedIds.clear();
            cart.items.forEach(function (item) {
                if (item.available) {
                    selectedIds.add(item.id);
                }
            });
        } else {
            const existingIds = new Set(cart.items.map(function (item) { return item.id; }));
            Array.from(selectedIds).forEach(function (id) {
                if (!existingIds.has(id)) {
                    selectedIds.delete(id);
                }
            });
        }

        loading.hidden = true;
        content.hidden = cart.items.length === 0;
        empty.hidden = cart.items.length !== 0;
        list.textContent = '';
        cart.items.forEach(function (item) {
            list.appendChild(createItem(item));
        });
        updateSelectionSummary();
        window.dispatchEvent(new CustomEvent('cart:updated', {
            detail: { totalQuantity: cart.totalQuantity }
        }));
    }

    function request(url, options) {
        content.classList.add('is-loading');
        return window.authFetch(url, options || {})
            .then(function (response) {
                return response.json().catch(function () { return {}; }).then(function (data) {
                    if (!response.ok) {
                        throw new Error(data.message || '장바구니 요청에 실패했습니다.');
                    }
                    return data;
                });
            })
            .finally(function () {
                content.classList.remove('is-loading');
            });
    }

    list.addEventListener('change', function (event) {
        const checkbox = event.target.closest('.cart-item-check');
        if (!checkbox) {
            return;
        }
        const id = Number(checkbox.closest('.cart-item').dataset.itemId);
        if (checkbox.checked) {
            selectedIds.add(id);
        } else {
            selectedIds.delete(id);
        }
        updateSelectionSummary();
    });

    list.addEventListener('click', function (event) {
        const row = event.target.closest('.cart-item');
        if (!row) {
            return;
        }
        const itemId = Number(row.dataset.itemId);
        const item = cart.items.find(function (candidate) { return candidate.id === itemId; });
        if (event.target.closest('.cart-item-remove')) {
            request('/api/v1/cart/items?ids=' + itemId, { method: 'DELETE' })
                .then(function (data) { render(data, false); })
                .catch(function (error) { showMessage(error.message, true); });
            return;
        }
        const delta = event.target.closest('.cart-qty-minus') ? -1
            : event.target.closest('.cart-qty-plus') ? 1 : 0;
        if (!delta || !item) {
            return;
        }
        request('/api/v1/cart/items/' + itemId, {
            method: 'PATCH',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ quantity: item.quantity + delta })
        })
            .then(function (data) { render(data, false); })
            .catch(function (error) { showMessage(error.message, true); });
    });

    selectAll.addEventListener('change', function () {
        cart.items.forEach(function (item) {
            if (item.available) {
                if (selectAll.checked) {
                    selectedIds.add(item.id);
                } else {
                    selectedIds.delete(item.id);
                }
            }
        });
        render(cart, false);
    });

    deleteSelected.addEventListener('click', function () {
        if (selectedIds.size === 0) {
            showMessage('삭제할 상품을 선택해주세요.', true);
            return;
        }
        const query = Array.from(selectedIds).map(function (id) { return 'ids=' + id; }).join('&');
        request('/api/v1/cart/items?' + query, { method: 'DELETE' })
            .then(function (data) {
                showMessage('선택한 상품을 삭제했습니다.', false);
                render(data, false);
            })
            .catch(function (error) { showMessage(error.message, true); });
    });

    const storedNotice = sessionStorage.getItem('cartNotice');
    if (storedNotice) {
        showMessage(storedNotice, false);
        sessionStorage.removeItem('cartNotice');
    }

    request('/api/v1/cart')
        .then(function (data) { render(data, true); })
        .catch(function (error) {
            loading.hidden = true;
            empty.hidden = false;
            showMessage(error.message, true);
        });
});
