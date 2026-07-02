document.addEventListener('DOMContentLoaded', function () {
    const toggles = document.querySelectorAll('.footer-column-toggle');

    toggles.forEach(function (toggle) {
        const list = document.getElementById(toggle.getAttribute('aria-controls'));

        if (!list) {
            return;
        }

        toggle.addEventListener('click', function () {
            const isOpen = list.classList.toggle('is-open');
            toggle.classList.toggle('is-open', isOpen);
            toggle.setAttribute('aria-expanded', String(isOpen));
        });
    });
});
