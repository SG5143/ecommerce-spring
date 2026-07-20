document.addEventListener('DOMContentLoaded', function () {
    // ── 이미지 갤러리: 썸네일 클릭·좌우 버튼으로 대표 이미지 전환 ──
    const mainImage = document.getElementById('gallery-image');
    const thumbs = Array.from(document.querySelectorAll('.gallery-thumb'));
    let currentIndex = 0;

    function showImage(index) {
        if (!mainImage || thumbs.length === 0) {
            return;
        }
        currentIndex = (index + thumbs.length) % thumbs.length;
        mainImage.src = thumbs[currentIndex].querySelector('img').src;
        thumbs.forEach(function (thumb, i) {
            thumb.classList.toggle('is-active', i === currentIndex);
        });
    }

    thumbs.forEach(function (thumb) {
        thumb.addEventListener('click', function () {
            showImage(Number(thumb.dataset.index));
        });
    });

    const prevBtn = document.querySelector('.gallery-btn-prev');
    const nextBtn = document.querySelector('.gallery-btn-next');
    if (prevBtn) {
        prevBtn.addEventListener('click', function () {
            showImage(currentIndex - 1);
        });
    }
    if (nextBtn) {
        nextBtn.addEventListener('click', function () {
            showImage(currentIndex + 1);
        });
    }

    // ── 옵션 선택·수량 조절·총 상품금액 계산 ──
    const infoRoot = document.querySelector('.detail-info');
    const selectedList = document.getElementById('selected-options');
    const optionSelect = document.getElementById('option-select');
    const totalPrice = document.getElementById('total-price');
    const totalCount = document.getElementById('total-count');

    if (!infoRoot || !selectedList) {
        return;
    }

    const basePrice = Number(infoRoot.dataset.basePrice);

    function formatWon(amount) {
        return amount.toLocaleString('ko-KR');
    }

    function rowQty(row) {
        return Number(row.querySelector('.qty-value').textContent);
    }

    function updateTotal() {
        let total = 0;
        let count = 0;
        selectedList.querySelectorAll('.selected-option').forEach(function (row) {
            const unitPrice = basePrice + Number(row.dataset.extraPrice);
            const qty = rowQty(row);
            row.querySelector('.selected-option-price').textContent = formatWon(unitPrice * qty) + '원';
            total += unitPrice * qty;
            count += qty;
        });
        totalPrice.textContent = '₩' + formatWon(total);
        totalCount.textContent = '(' + count + '개)';
    }

    function changeQty(row, delta) {
        const stock = Number(row.dataset.stock);
        const next = rowQty(row) + delta;
        if (next < 1) {
            return;
        }
        if (next > stock) {
            alert('재고가 부족합니다. (최대 ' + stock + '개)');
            return;
        }
        row.querySelector('.qty-value').textContent = next;
        updateTotal();
    }

    function appendOptionRow(option) {
        const row = document.createElement('li');
        row.className = 'selected-option';
        row.dataset.optionId = option.value;
        row.dataset.extraPrice = option.dataset.extraPrice;
        row.dataset.stock = option.dataset.stock;

        const name = document.createElement('span');
        name.className = 'selected-option-name';
        name.textContent = option.dataset.name;

        const controls = document.createElement('div');
        controls.className = 'selected-option-controls';
        controls.innerHTML =
            '<div class="qty-stepper">' +
            '<button type="button" class="qty-btn qty-btn-minus" aria-label="수량 줄이기">&minus;</button>' +
            '<span class="qty-value">1</span>' +
            '<button type="button" class="qty-btn qty-btn-plus" aria-label="수량 늘리기">+</button>' +
            '</div>' +
            '<span class="selected-option-price"></span>' +
            '<button type="button" class="selected-option-remove" aria-label="옵션 삭제">&times;</button>';

        row.appendChild(name);
        row.appendChild(controls);
        selectedList.appendChild(row);
    }

    if (optionSelect) {
        optionSelect.addEventListener('change', function () {
            const option = optionSelect.selectedOptions[0];
            if (!option || option.value === '') {
                return;
            }
            const existing = selectedList.querySelector('.selected-option[data-option-id="' + option.value + '"]');
            if (existing) {
                changeQty(existing, 1);
            } else {
                appendOptionRow(option);
                updateTotal();
            }
            optionSelect.selectedIndex = 0;
        });
    }

    selectedList.addEventListener('click', function (event) {
        const row = event.target.closest('.selected-option');
        if (!row) {
            return;
        }
        if (event.target.closest('.qty-btn-minus')) {
            changeQty(row, -1);
        } else if (event.target.closest('.qty-btn-plus')) {
            changeQty(row, 1);
        } else if (event.target.closest('.selected-option-remove')) {
            row.remove();
            updateTotal();
        }
    });

    updateTotal();

    // ── 하단 아코디언 (제품 상세 정보 / 배송 / 교환 & 반품) ──
    document.querySelectorAll('.detail-accordion-toggle').forEach(function (toggle) {
        toggle.addEventListener('click', function () {
            const body = document.getElementById(toggle.getAttribute('aria-controls'));
            const isOpen = toggle.getAttribute('aria-expanded') === 'true';
            toggle.setAttribute('aria-expanded', String(!isOpen));
            toggle.classList.toggle('is-open', !isOpen);
            body.hidden = isOpen;
        });
    });
});
