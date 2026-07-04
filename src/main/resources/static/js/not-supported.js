document.addEventListener('DOMContentLoaded', function () {
    const overlay = document.getElementById('not-supported-overlay');
    const closeBtn = document.getElementById('not-supported-close');

    if (!overlay || !closeBtn) {
        return;
    }

    function openModal() {
        overlay.classList.add('is-open');
    }

    function closeModal() {
        overlay.classList.remove('is-open');
    }

    document.addEventListener('click', function (event) {
        const link = event.target.closest('a[href]');
        if (!link) {
            return;
        }

        if (!link.classList.contains('not-implemented')) {
            return;
        }

        event.preventDefault();
        openModal();
    });

    document.querySelectorAll('form.not-implemented').forEach(function (form) {
        form.addEventListener('submit', function (event) {
            event.preventDefault();
            openModal();
        });
    });

    closeBtn.addEventListener('click', closeModal);
    overlay.addEventListener('click', function (event) {
        if (event.target === overlay) {
            closeModal();
        }
    });

    document.addEventListener('keydown', function (event) {
        if (event.key === 'Escape') {
            closeModal();
        }
    });
});
