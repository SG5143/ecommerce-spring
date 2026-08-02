document.addEventListener('DOMContentLoaded', function () {
    const loading = document.getElementById('order-history-loading');
    const error = document.getElementById('order-history-error');
    const errorMessage = document.getElementById('order-history-error-message');
    const retryButton = document.getElementById('order-history-retry');
    const empty = document.getElementById('order-history-empty');
    const list = document.getElementById('order-history-list');
    const pagination = document.getElementById('order-history-pagination');
    const previousButton = document.getElementById('order-history-previous');
    const nextButton = document.getElementById('order-history-next');
    const pageNumbers = document.getElementById('order-history-page-numbers');
    const PAGE_BLOCK_SIZE = 5;

    const ORDER_STATUS = {
        PENDING_PAYMENT: { label: '결제 대기', className: 'is-pending' },
        PAID: { label: '결제 완료', className: 'is-paid' },
        PREPARING: { label: '상품 준비중', className: 'is-preparing' },
        SHIPPING: { label: '배송중', className: 'is-shipping' },
        DELIVERED: { label: '배송 완료', className: 'is-delivered' },
        CANCELLED: { label: '주문 취소', className: 'is-cancelled' },
        RETURN_REQUESTED: { label: '반품 요청', className: 'is-returned' },
        RETURNED: { label: '반품 완료', className: 'is-returned' }
    };
    const PAYMENT_STATUS = {
        PENDING: '결제 대기',
        PROCESSING: '승인 확인중',
        SUCCESS: '결제 완료',
        FAILED: '결제 실패',
        CANCELLED: '결제 취소',
        REFUND_PENDING: '환불 처리중',
        REFUNDED: '환불 완료',
        REFUND_FAILED: '환불 실패'
    };
    const PAYMENT_METHOD = {
        CARD: '카드'
    };
    let currentPage = readPageFromUrl();

    function readPageFromUrl() {
        const rawPage = Number(new URLSearchParams(window.location.search).get('page'));
        return Number.isInteger(rawPage) && rawPage > 0 ? rawPage : 1;
    }

    function loadOrders(pageNumber) {
        currentPage = pageNumber;
        showLoading();

        window.authFetch('/api/v1/orders?page=' + encodeURIComponent(pageNumber - 1))
            .then(function (response) {
                return response.json().catch(function () {
                    return {};
                }).then(function (data) {
                    if (!response.ok) {
                        throw new Error(data.message || '주문내역을 불러오지 못했습니다.');
                    }
                    return data;
                });
            })
            .then(function (data) {
                render(data);
                updateUrl(pageNumber);
            })
            .catch(function (requestError) {
                showError(requestError.message);
            });
    }

    function showLoading() {
        loading.hidden = false;
        error.hidden = true;
        empty.hidden = true;
        pagination.hidden = true;
        list.replaceChildren();
    }

    function showError(message) {
        loading.hidden = true;
        error.hidden = false;
        empty.hidden = true;
        pagination.hidden = true;
        errorMessage.textContent = message || '주문내역을 불러오지 못했습니다.';
    }

    function render(data) {
        loading.hidden = true;
        error.hidden = true;
        list.replaceChildren();

        const orders = Array.isArray(data.orders) ? data.orders : [];
        if (orders.length === 0) {
            empty.hidden = false;
            pagination.hidden = true;
            return;
        }

        empty.hidden = true;
        orders.forEach(function (order, index) {
            list.appendChild(createOrderCard(order, index));
        });
        renderPagination(data);
    }

    function createOrderCard(order, index) {
        const card = document.createElement('article');
        card.className = 'order-history-card';

        const header = document.createElement('header');
        header.className = 'order-history-card-head';
        const heading = document.createElement('div');
        appendText(heading, 'time', 'order-history-date', formatDateTime(order.orderedAt));
        appendText(heading, 'strong', 'order-history-number', '주문번호 ' + order.orderNumber);
        header.appendChild(heading);

        const status = ORDER_STATUS[order.orderStatus]
            || { label: order.orderStatus || '-', className: 'is-pending' };
        appendText(header, 'span', 'order-history-status ' + status.className, status.label);
        card.appendChild(header);

        const itemList = document.createElement('ul');
        itemList.className = 'order-history-items';
        (order.items || []).forEach(function (item) {
            itemList.appendChild(createItem(item));
        });
        card.appendChild(itemList);

        const detailsId = 'order-history-details-' + currentPage + '-' + index;
        const detailsToggle = document.createElement('button');
        detailsToggle.type = 'button';
        detailsToggle.className = 'order-history-details-toggle';
        detailsToggle.setAttribute('aria-expanded', 'false');
        detailsToggle.setAttribute('aria-controls', detailsId);
        appendText(detailsToggle, 'span', '', '결제 상세');
        const arrow = document.createElement('img');
        arrow.className = 'order-history-details-arrow';
        arrow.src = '/images/icons/select-icon.svg';
        arrow.alt = '';
        detailsToggle.appendChild(arrow);
        card.appendChild(detailsToggle);

        const details = document.createElement('div');
        details.className = 'order-history-details';
        details.id = detailsId;
        details.hidden = true;
        details.appendChild(createAmountDetails(order));
        details.appendChild(createPaymentDetails(order.payment));
        card.appendChild(details);

        detailsToggle.addEventListener('click', function () {
            const isOpen = detailsToggle.getAttribute('aria-expanded') === 'true';
            detailsToggle.setAttribute('aria-expanded', String(!isOpen));
            detailsToggle.classList.toggle('is-open', !isOpen);
            details.hidden = isOpen;
        });
        return card;
    }

    function createItem(item) {
        const row = document.createElement('li');
        row.className = 'order-history-item';

        if (item.thumbnailUrl) {
            const image = document.createElement('img');
            image.className = 'order-history-item-image';
            image.src = item.thumbnailUrl;
            image.alt = '';
            row.appendChild(image);
        } else {
            const placeholder = document.createElement('span');
            placeholder.className = 'order-history-item-image';
            placeholder.setAttribute('aria-hidden', 'true');
            row.appendChild(placeholder);
        }

        const info = document.createElement('div');
        info.className = 'order-history-item-info';
        appendText(info, 'strong', '', item.productName);
        if (item.optionName) {
            appendText(info, 'span', '', '옵션: ' + item.optionName);
        }
        appendText(info, 'span', '', '수량: ' + item.quantity + '개 · 단가 ' + formatWon(item.unitPrice));
        row.appendChild(info);
        appendText(row, 'strong', 'order-history-item-price', formatWon(item.lineAmount));
        return row;
    }

    function createAmountDetails(order) {
        const section = document.createElement('section');
        section.className = 'order-history-detail-section';
        appendText(section, 'h2', '', '결제 금액');
        const details = document.createElement('dl');
        appendDetail(details, '상품금액', formatWon(order.merchandiseAmount));
        appendDetail(details, '할인금액', '-' + formatWon(order.discountAmount));
        appendDetail(details, '배송비', formatWon(order.shippingFee));
        appendDetail(details, '최종 결제금액', formatWon(order.totalAmount), true);
        section.appendChild(details);
        return section;
    }

    function createPaymentDetails(payment) {
        const section = document.createElement('section');
        section.className = 'order-history-detail-section';
        appendText(section, 'h2', '', '결제 정보');
        const details = document.createElement('dl');
        if (!payment) {
            appendDetail(details, '결제상태', '결제 시도 없음');
        } else {
            appendDetail(details, '결제번호', payment.paymentNumber);
            appendDetail(details, '결제상태', PAYMENT_STATUS[payment.paymentStatus] || payment.paymentStatus);
            appendDetail(
                details,
                '결제수단',
                PAYMENT_METHOD[payment.paymentMethod] || payment.paymentMethod || '-'
            );
            appendDetail(details, '결제 제공자', payment.pgProvider || '-');
            appendDetail(details, '승인일시', formatDateTime(payment.approvedAt));
        }
        section.appendChild(details);
        return section;
    }

    function appendDetail(parent, term, description, emphasized) {
        const row = document.createElement('div');
        if (emphasized) {
            row.className = 'is-total';
        }
        appendText(row, 'dt', '', term);
        appendText(row, 'dd', '', description);
        parent.appendChild(row);
    }

    function renderPagination(data) {
        const totalPages = Number(data.totalPages) || 0;
        if (totalPages <= 1) {
            pagination.hidden = true;
            return;
        }

        pagination.hidden = false;
        pageNumbers.replaceChildren();
        const currentIndex = Number(data.page) || 0;
        const blockStart = Math.floor(currentIndex / PAGE_BLOCK_SIZE) * PAGE_BLOCK_SIZE;
        const blockEnd = Math.min(blockStart + PAGE_BLOCK_SIZE, totalPages);

        previousButton.disabled = !data.hasPrevious;
        previousButton.onclick = function () {
            if (data.hasPrevious) {
                loadOrders(currentIndex);
            }
        };
        nextButton.disabled = !data.hasNext;
        nextButton.onclick = function () {
            if (data.hasNext) {
                loadOrders(currentIndex + 2);
            }
        };

        for (let index = blockStart; index < blockEnd; index += 1) {
            const pageButton = document.createElement('button');
            pageButton.type = 'button';
            pageButton.textContent = String(index + 1);
            pageButton.setAttribute('aria-label', (index + 1) + '페이지');
            if (index === currentIndex) {
                pageButton.className = 'is-current';
                pageButton.setAttribute('aria-current', 'page');
            }
            pageButton.addEventListener('click', function () {
                loadOrders(index + 1);
            });
            pageNumbers.appendChild(pageButton);
        }
    }

    function updateUrl(pageNumber) {
        const url = new URL(window.location.href);
        if (pageNumber === 1) {
            url.searchParams.delete('page');
        } else {
            url.searchParams.set('page', String(pageNumber));
        }
        window.history.replaceState({}, '', url);
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
            return String(value).replace('T', ' ');
        }
        return new Intl.DateTimeFormat('ko-KR', {
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit'
        }).format(date);
    }

    function appendText(parent, tagName, className, text) {
        const element = document.createElement(tagName);
        if (className) {
            element.className = className;
        }
        element.textContent = text == null || text === '' ? '-' : String(text);
        parent.appendChild(element);
        return element;
    }

    retryButton.addEventListener('click', function () {
        loadOrders(currentPage);
    });

    const logoutButton = document.getElementById('myshop-logout');
    if (logoutButton) {
        logoutButton.addEventListener('click', function () {
            logoutButton.disabled = true;
            fetch('/api/v1/auth/logout', { method: 'POST' })
                .catch(function () {
                    // 네트워크 오류여도 클라이언트 로그아웃은 진행한다.
                })
                .then(function () {
                    window.clearAccessToken();
                    window.location.href = '/';
                });
        });
    }

    loadOrders(currentPage);
});
