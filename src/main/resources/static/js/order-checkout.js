document.addEventListener('DOMContentLoaded', function () {
    const state = window.checkoutState;
    const selection = state.getSelection();
    const pending = state.getPending();
    const form = document.getElementById('checkout-order-form');
    const loading = document.getElementById('checkout-loading');
    const message = document.getElementById('checkout-message');
    const submitButton = document.getElementById('checkout-order-submit');
    const itemList = document.getElementById('checkout-order-items');
    const loggedIn = !!window.getAccessToken();
    const ownerType = loggedIn ? 'MEMBER' : 'GUEST';
    let selectedItems = [];

    if (pending) {
        window.location.replace('/checkout/payment');
        return;
    }
    if (!selection) {
        returnToCart('주문할 상품을 장바구니에서 다시 선택해주세요.');
        return;
    }

    function formatWon(amount) {
        return '₩' + Number(amount || 0).toLocaleString('ko-KR');
    }

    function value(id) {
        const element = document.getElementById(id);
        return element ? element.value.trim() : '';
    }

    function setValue(id, nextValue) {
        const element = document.getElementById(id);
        if (element) {
            element.value = nextValue || '';
        }
    }

    function showMessage(text) {
        message.textContent = text || '';
        if (text) {
            message.scrollIntoView({ behavior: 'smooth', block: 'center' });
        }
    }

    function returnToCart(text) {
        sessionStorage.setItem('cartNotice', text);
        window.location.replace('/cart');
    }

    function requestJson(url, options) {
        return window.authFetch(url, options || {}).then(function (response) {
            return response.json().catch(function () { return {}; }).then(function (data) {
                if (!response.ok) {
                    const error = new Error(data.message || '요청을 처리하지 못했습니다.');
                    error.status = response.status;
                    throw error;
                }
                return data;
            });
        });
    }

    function appendText(parent, tagName, className, text) {
        const element = document.createElement(tagName);
        element.className = className;
        element.textContent = text;
        parent.appendChild(element);
        return element;
    }

    function renderItems(items) {
        itemList.textContent = '';
        let total = 0;
        items.forEach(function (item) {
            const row = document.createElement('li');
            row.className = 'checkout-order-item';
            if (item.thumbnailUrl) {
                const image = document.createElement('img');
                image.src = item.thumbnailUrl;
                image.alt = '';
                image.className = 'checkout-order-image';
                row.appendChild(image);
            } else {
                const imagePlaceholder = document.createElement('span');
                imagePlaceholder.className = 'checkout-order-image';
                row.appendChild(imagePlaceholder);
            }
            const info = document.createElement('div');
            info.className = 'checkout-order-info';
            appendText(info, 'strong', '', item.productName);
            if (item.optionName) {
                appendText(info, 'span', '', '옵션: ' + item.optionName);
            }
            appendText(info, 'span', '', '수량: ' + item.quantity);
            row.appendChild(info);
            appendText(row, 'strong', 'checkout-order-price', formatWon(item.lineTotal));
            itemList.appendChild(row);
            total += Number(item.lineTotal) || 0;
        });
        document.getElementById('checkout-merchandise-total').textContent = formatWon(total);
        document.getElementById('checkout-total-amount').textContent = formatWon(total);
    }

    function fillMember(member) {
        document.getElementById('checkout-member-orderer').hidden = false;
        document.getElementById('checkout-guest-orderer').hidden = true;
        document.getElementById('checkout-copy-orderer-label').hidden = true;
        setValue('member-orderer-name', member.name);
        setValue('member-orderer-phone', member.phone);
        setValue('member-orderer-email', member.email);
        setValue('receiver-name', member.name);
        setValue('receiver-phone', member.phone);
        setValue('receiver-zipcode', member.zipcode);
        setValue('receiver-address', member.address);
        setValue('receiver-address-detail', member.addressDetail);
    }

    function prepareGuest() {
        document.getElementById('checkout-member-orderer').hidden = true;
        document.getElementById('checkout-guest-orderer').hidden = false;
        document.getElementById('checkout-copy-orderer-label').hidden = false;
    }

    function clearFieldErrors() {
        document.querySelectorAll('.checkout-input.is-error').forEach(function (input) {
            input.classList.remove('is-error');
            input.removeAttribute('aria-invalid');
        });
        document.querySelectorAll('.checkout-field-error').forEach(function (error) {
            error.textContent = '';
        });
    }

    function setFieldError(id, text) {
        const input = document.getElementById(id);
        const error = document.querySelector('[data-error-for="' + id + '"]');
        if (input) {
            input.classList.add('is-error');
            input.setAttribute('aria-invalid', 'true');
        }
        if (error) {
            error.textContent = text;
        }
        return input;
    }

    function validate() {
        clearFieldErrors();
        const errors = [];

        function require(id, requiredMessage) {
            if (!value(id)) {
                errors.push(setFieldError(id, requiredMessage));
            }
        }

        if (!loggedIn) {
            require('orderer-name', '주문자명을 입력해주세요.');
            require('orderer-phone', '주문자 연락처를 입력해주세요.');
            const email = value('orderer-email');
            if (email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
                errors.push(setFieldError('orderer-email', '올바른 이메일 형식이 아닙니다.'));
            }
        }
        require('receiver-name', '수령인명을 입력해주세요.');
        require('receiver-phone', '수령인 연락처를 입력해주세요.');
        require('receiver-zipcode', '우편번호를 입력해주세요.');
        require('receiver-address', '주소를 입력해주세요.');

        if (errors.length > 0) {
            showMessage('입력한 주문 정보를 다시 확인해주세요.');
            const first = errors.find(Boolean);
            if (first) {
                first.focus();
            }
            return false;
        }
        showMessage('');
        return true;
    }

    function buildRequest() {
        return {
            cartItemIds: selection.cartItemIds,
            orderer: loggedIn ? null : {
                name: value('orderer-name'),
                phone: value('orderer-phone'),
                email: value('orderer-email') || null
            },
            receiver: {
                name: value('receiver-name'),
                phone: value('receiver-phone'),
                zipcode: value('receiver-zipcode'),
                address: value('receiver-address'),
                addressDetail: value('receiver-address-detail') || null
            },
            deliveryMessage: value('delivery-message') || null
        };
    }

    const cartRequest = requestJson('/api/v1/cart');
    const memberRequest = loggedIn ? requestJson('/api/v1/members/me/detail') : Promise.resolve(null);

    Promise.all([cartRequest, memberRequest])
        .then(function (results) {
            const cart = results[0];
            const itemById = new Map((cart.items || []).map(function (item) {
                return [Number(item.id), item];
            }));
            selectedItems = selection.cartItemIds.map(function (id) {
                return itemById.get(Number(id));
            });
            if (selectedItems.some(function (item) { return !item || !item.available; })) {
                state.clearSelection();
                returnToCart('선택한 상품의 판매 상태나 재고가 변경되었습니다. 장바구니에서 다시 확인해주세요.');
                return;
            }
            renderItems(selectedItems);
            if (loggedIn) {
                fillMember(results[1]);
            } else {
                prepareGuest();
            }
            loading.hidden = true;
            form.hidden = false;
        })
        .catch(function (error) {
            loading.hidden = true;
            if (error.status === 404) {
                state.clearSelection();
                returnToCart(error.message);
                return;
            }
            showMessage(error.status ? error.message : '네트워크 오류로 주문 정보를 불러오지 못했습니다.');
        });

    document.getElementById('checkout-copy-orderer').addEventListener('change', function (event) {
        if (event.target.checked) {
            setValue('receiver-name', value('orderer-name'));
            setValue('receiver-phone', value('orderer-phone'));
        }
    });

    const deliveryMessage = document.getElementById('delivery-message');
    deliveryMessage.addEventListener('input', function () {
        document.getElementById('delivery-message-length').textContent = String(deliveryMessage.value.length);
    });

    form.addEventListener('input', function (event) {
        const input = event.target.closest('.checkout-input');
        if (!input || !input.id) {
            return;
        }
        input.classList.remove('is-error');
        input.removeAttribute('aria-invalid');
        const error = document.querySelector('[data-error-for="' + input.id + '"]');
        if (error) {
            error.textContent = '';
        }
    });

    const zipcodeOverlay = document.getElementById('checkout-zipcode-overlay');
    const zipcodeEmbed = document.getElementById('checkout-zipcode-embed');

    function closeZipcode() {
        zipcodeOverlay.classList.remove('is-open');
    }

    document.getElementById('checkout-zipcode-search').addEventListener('click', function () {
        if (!window.daum || !window.daum.Postcode) {
            showMessage('주소 검색 서비스를 불러오지 못했습니다. 잠시 후 다시 시도해주세요.');
            return;
        }
        zipcodeOverlay.classList.add('is-open');
        zipcodeEmbed.textContent = '';
        new window.daum.Postcode({
            oncomplete: function (data) {
                setValue('receiver-zipcode', data.zonecode);
                setValue('receiver-address', data.roadAddress || data.address);
                document.getElementById('receiver-address-detail').focus();
                closeZipcode();
            },
            width: '100%',
            height: '100%'
        }).embed(zipcodeEmbed);
    });
    document.getElementById('checkout-zipcode-close').addEventListener('click', closeZipcode);
    zipcodeOverlay.addEventListener('click', function (event) {
        if (event.target === zipcodeOverlay) {
            closeZipcode();
        }
    });

    form.addEventListener('submit', function (event) {
        event.preventDefault();
        if (!validate() || selectedItems.length === 0) {
            return;
        }
        submitButton.disabled = true;
        submitButton.textContent = '주문서를 생성하는 중...';
        requestJson('/api/v1/orders', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(buildRequest())
        })
            .then(function (order) {
                state.setPending(order, ownerType);
                window.location.href = '/checkout/payment';
            })
            .catch(function (error) {
                submitButton.disabled = false;
                submitButton.textContent = '주문서 생성';
                if (error.status === 404) {
                    state.clearSelection();
                    returnToCart(error.message);
                    return;
                }
                showMessage(error.status ? error.message : '네트워크 오류가 발생했습니다. 입력값을 유지한 채 다시 시도해주세요.');
            });
    });
});
