document.addEventListener('DOMContentLoaded', function () {
    const carousels = document.querySelectorAll('.product-carousel');

    carousels.forEach(function (carousel) {
        const track = carousel.querySelector('.carousel-track');
        const prevBtn = carousel.querySelector('.carousel-btn-prev');
        const nextBtn = carousel.querySelector('.carousel-btn-next');

        if (!track || !prevBtn || !nextBtn) {
            return;
        }

        function updateButtons() {
            prevBtn.disabled = track.scrollLeft <= 0;
            nextBtn.disabled = track.scrollLeft >= track.scrollWidth - track.clientWidth - 1;
        }

        prevBtn.addEventListener('click', function () {
            track.scrollBy({left: -track.clientWidth, behavior: 'smooth'});
        });

        nextBtn.addEventListener('click', function () {
            track.scrollBy({left: track.clientWidth, behavior: 'smooth'});
        });

        track.addEventListener('scroll', updateButtons);

        updateButtons();
    });
});
