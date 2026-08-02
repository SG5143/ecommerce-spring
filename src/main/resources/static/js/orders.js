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

    const DISPLAY_STATUS = {
        PAYMENT_PENDING: { label: '결제 대기', className: 'is-pending', dateLabel: '결제 요청일' },
        PAYMENT_PROCESSING: { label: '승인 확인중', className: 'is-pending', dateLabel: '승인 요청일' },
        PAYMENT_COMPLETED: { label: '결제 완료', className: 'is-paid', dateLabel: '결제일' },
        PAYMENT_FAILED: { label: '결제 실패', className: 'is-cancelled', dateLabel: '실패일' },
        PAYMENT_CANCELLED: { label: '결제 취소', className: 'is-cancelled', dateLabel: '취소일' },
        PREPARING: { label: '상품 준비중', className: 'is-preparing', dateLabel: '결제일' },
        SHIPPING: { label: '배송중', className: 'is-shipping', dateLabel: '배송 시작일' },
        DELIVERED: { label: '배송 완료', className: 'is-delivered', dateLabel: '배송 완료일' },
        ORDER_CANCELLED: { label: '주문 취소', className: 'is-cancelled', dateLabel: '주문 취소일' },
        RETURN_REQUESTED: { label: '반품 요청', className: 'is-returned', dateLabel: '반품 요청일' },
        RETURNED: { label: '반품 완료', className: 'is-returned', dateLabel: '반품 완료일' },
        REFUND_PENDING: { label: '환불 처리중', className: 'is-pending', dateLabel: '환불 요청일' },
        REFUNDED: { label: '환불 완료', className: 'is-returned', dateLabel: '환불 완료일' },
        REFUND_FAILED: { label: '환불 실패', className: 'is-cancelled', dateLabel: '환불 실패일' }
    };
    const PAYMENT_STATUS = {
        PENDING: { label: '결제 대기', dateLabel: '결제 요청일시' },
        PROCESSING: { label: '승인 확인중', dateLabel: '승인 요청일시' },
        SUCCESS: { label: '결제 완료', dateLabel: '결제일시' },
        FAILED: { label: '결제 실패', dateLabel: '실패일시' },
        CANCELLED: { label: '결제 취소', dateLabel: '취소일시' },
        REFUND_PENDING: { label: '환불 처리중', dateLabel: '환불 요청일시' },
        REFUNDED: { label: '환불 완료', dateLabel: '환불 완료일시' },
        REFUND_FAILED: { label: '환불 실패', dateLabel: '환불 실패일시' }
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
        heading.className = 'order-history-card-heading';
        appendText(heading, 'time', 'order-history-date', '주문일 ' + formatDateTime(order.orderedAt));
        appendText(heading, 'strong', 'order-history-number', '' + order.orderNumber);
        header.appendChild(heading);

        const status = DISPLAY_STATUS[order.displayStatus]
            || { label: order.displayStatus || '-', className: 'is-pending', dateLabel: '처리일' };
        const statusGroup = document.createElement('div');
        statusGroup.className = 'order-history-status-group';
        appendText(statusGroup, 'span', 'order-history-status ' + status.className, status.label);
        if (order.statusChangedAt) {
            appendText(
                statusGroup,
                'time',
                'order-history-status-date',
                status.dateLabel + ' ' + formatDateTime(order.statusChangedAt)
            );
        }
        header.appendChild(statusGroup);
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
        const totalLabel = isCompletedPaymentDisplay(order.displayStatus)
            ? '최종 결제금액'
            : '주문금액';
        appendDetail(details, totalLabel, formatWon(order.totalAmount), true);
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
            const paymentStatus = PAYMENT_STATUS[payment.paymentStatus]
                || { label: payment.paymentStatus || '-', dateLabel: '처리일시' };
            appendDetail(details, '결제번호', payment.paymentNumber);
            appendDetail(details, '결제상태', paymentStatus.label);
            appendDetail(details, '결제금액', formatWon(payment.amount));
            appendDetail(
                details,
                '결제수단',
                PAYMENT_METHOD[payment.paymentMethod] || payment.paymentMethod || '-'
            );
            appendDetail(details, '결제 제공자', payment.pgProvider || '-');
            if (payment.statusChangedAt) {
                appendDetail(details, paymentStatus.dateLabel, formatDateTime(payment.statusChangedAt));
            }
            if (payment.paymentStatus === 'FAILED' || payment.paymentStatus === 'CANCELLED') {
                const fallbackReason = payment.paymentStatus === 'CANCELLED'
                    ? '결제가 취소되었습니다.'
                    : '결제를 완료하지 못했습니다.';
                appendDetail(details, '사유', payment.failureReason || fallbackReason);
            }
        }
        section.appendChild(details);
        return section;
    }

    function isCompletedPaymentDisplay(displayStatus) {
        return [
            'PAYMENT_COMPLETED',
            'PREPARING',
            'SHIPPING',
            'DELIVERED',
            'RETURN_REQUESTED',
            'RETURNED',
            'REFUND_PENDING',
            'REFUNDED',
            'REFUND_FAILED'
        ].includes(displayStatus);
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
