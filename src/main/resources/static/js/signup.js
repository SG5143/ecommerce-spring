document.addEventListener('DOMContentLoaded', function () {
    function setFieldMessage(input, currentMessageEl, text, isError) {
        if (!isError) {
            if (currentMessageEl) {
                currentMessageEl.remove();
            }
            return null;
        }

        if (!currentMessageEl) {
            currentMessageEl = document.createElement('p');
            currentMessageEl.className = 'signup-field-message is-error';
            input.insertAdjacentElement('afterend', currentMessageEl);
        }
        currentMessageEl.textContent = text;
        return currentMessageEl;
    }

    const usernameInput = document.getElementById('signup-username');
    let usernameMessage = null;

    if (usernameInput) {
        const DEBOUNCE_DELAY_MS = 400;
        let debounceTimer = null;

        function checkUsername() {
            const memberId = usernameInput.value.trim();

            fetch('/api/v1/members/check-id?member_id=' + encodeURIComponent(memberId))
                .then(function (response) {
                    return response.json();
                })
                .then(function (data) {
                    usernameMessage = setFieldMessage(usernameInput, usernameMessage, data.msg, !data.passed);
                })
                .catch(function () {
                    usernameMessage = setFieldMessage(usernameInput, usernameMessage, '아이디 확인 중 오류가 발생했습니다.', true);
                });
        }

        usernameInput.addEventListener('input', function () {
            usernameMessage = setFieldMessage(usernameInput, usernameMessage, '', false);

            clearTimeout(debounceTimer);
            if (!usernameInput.value.trim()) {
                return;
            }
            debounceTimer = setTimeout(checkUsername, DEBOUNCE_DELAY_MS);
        });
    }

    const passwordInput = document.getElementById('signup-password');
    const passwordConfirmInput = document.getElementById('signup-password-confirm');
    let passwordMessage = null;
    let passwordConfirmMessage = null;
    const PASSWORD_ALLOWED_PATTERN = /^[a-zA-Z0-9!@#$%^*+=.-]{8,20}$/;
    const PASSWORD_CHAR_GROUPS = [/[a-zA-Z]/, /[0-9]/, /[!@#$%^*+=.-]/];

    function isPasswordValid(value) {
        if (!PASSWORD_ALLOWED_PATTERN.test(value)) {
            return false;
        }
        const groupCount = PASSWORD_CHAR_GROUPS.filter(function (group) {
            return group.test(value);
        }).length;
        return groupCount >= 2;
    }

    function checkPasswordMatch() {
        const isMismatched = passwordConfirmInput.value && passwordInput.value !== passwordConfirmInput.value;
        passwordConfirmMessage = setFieldMessage(passwordConfirmInput, passwordConfirmMessage, '비밀번호가 일치하지 않습니다.', isMismatched);
    }

    if (passwordInput) {
        passwordInput.addEventListener('input', function () {
            const isInvalid = passwordInput.value && !isPasswordValid(passwordInput.value);
            passwordMessage = setFieldMessage(passwordInput, passwordMessage, '영문, 숫자, 특수문자(!@#$%^*+=.-) 중 2가지 이상을 조합한 8~20자여야 합니다.', isInvalid);

            if (passwordConfirmInput) {
                checkPasswordMatch();
            }
        });
    }

    if (passwordConfirmInput && passwordInput) {
        passwordConfirmInput.addEventListener('input', checkPasswordMatch);
    }

    const phoneInput = document.getElementById('signup-phone');
    const phoneVerifyButton = document.getElementById('signup-phone-verify-button');
    const phoneVerifyOverlay = document.getElementById('phone-verify-overlay');
    const phoneVerifyClose = document.getElementById('phone-verify-close');
    const phoneVerifyInput = document.getElementById('phone-verify-input');
    const phoneVerifyConfirm = document.getElementById('phone-verify-confirm');

    if (phoneInput && phoneVerifyButton && phoneVerifyOverlay && phoneVerifyClose && phoneVerifyInput && phoneVerifyConfirm) {
        function formatPhoneNumber(value) {
            const digits = value.replace(/\D/g, '').slice(0, 11);

            if (digits.length < 4) {
                return digits;
            }
            if (digits.length < 8) {
                return digits.slice(0, 3) + '-' + digits.slice(3);
            }
            return digits.slice(0, 3) + '-' + digits.slice(3, 7) + '-' + digits.slice(7);
        }

        phoneVerifyInput.addEventListener('input', function () {
            phoneVerifyInput.value = formatPhoneNumber(phoneVerifyInput.value);
        });

        function openPhoneVerifyModal() {
            phoneVerifyInput.value = '';
            phoneVerifyOverlay.classList.add('is-open');
        }

        function closePhoneVerifyModal() {
            phoneVerifyOverlay.classList.remove('is-open');
        }

        phoneVerifyButton.addEventListener('click', openPhoneVerifyModal);
        phoneInput.addEventListener('click', openPhoneVerifyModal);
        phoneVerifyClose.addEventListener('click', closePhoneVerifyModal);
        phoneVerifyOverlay.addEventListener('click', function (event) {
            if (event.target === phoneVerifyOverlay) {
                closePhoneVerifyModal();
            }
        });
        document.addEventListener('keydown', function (event) {
            if (event.key === 'Escape') {
                closePhoneVerifyModal();
            }
        });

        phoneVerifyConfirm.addEventListener('click', function () {
            const phoneValue = phoneVerifyInput.value.trim();
            if (!phoneValue) {
                return;
            }
            phoneInput.value = phoneValue;
            closePhoneVerifyModal();
        });
    }

    const zipcodeSearchButton = document.getElementById('signup-zipcode-search-button');
    const zipcodeInput = document.getElementById('signup-zipcode');
    const addressInput = document.getElementById('signup-address');
    const addressDetailInput = document.getElementById('signup-address-detail');
    const zipcodeOverlay = document.getElementById('zipcode-search-overlay');
    const zipcodeClose = document.getElementById('zipcode-search-close');
    const zipcodeEmbedTarget = document.getElementById('zipcode-search-embed');

    if (zipcodeSearchButton && zipcodeInput && addressInput && addressDetailInput
        && zipcodeOverlay && zipcodeClose && zipcodeEmbedTarget) {
        function openZipcodeModal() {
            zipcodeOverlay.classList.add('is-open');
            zipcodeEmbedTarget.innerHTML = '';
            new daum.Postcode({
                oncomplete: function (data) {
                    zipcodeInput.value = data.zonecode;
                    addressInput.value = data.roadAddress || data.address;
                    addressDetailInput.focus();
                    closeZipcodeModal();
                },
                width: '100%',
                height: '100%'
            }).embed(zipcodeEmbedTarget);
        }

        function closeZipcodeModal() {
            zipcodeOverlay.classList.remove('is-open');
        }

        zipcodeSearchButton.addEventListener('click', openZipcodeModal);
        zipcodeInput.addEventListener('click', openZipcodeModal);
        addressInput.addEventListener('click', openZipcodeModal);
        zipcodeClose.addEventListener('click', closeZipcodeModal);
        zipcodeOverlay.addEventListener('click', function (event) {
            if (event.target === zipcodeOverlay) {
                closeZipcodeModal();
            }
        });
        document.addEventListener('keydown', function (event) {
            if (event.key === 'Escape') {
                closeZipcodeModal();
            }
        });
    }

    const termsAllCheckbox = document.getElementById('terms-all');
    const termsMarketingCheckbox = document.getElementById('terms-marketing');
    const termsMarketingChildCheckboxes = Array.from(document.querySelectorAll('[data-terms-parent="terms-marketing"]'));
    const termsChildCheckboxes = Array.from(document.querySelectorAll('.terms-checkbox')).filter(function (checkbox) {
        return checkbox !== termsAllCheckbox;
    });

    if (termsAllCheckbox && termsChildCheckboxes.length) {
        function updateTermsAll() {
            termsAllCheckbox.checked = termsChildCheckboxes.every(function (checkbox) {
                return checkbox.checked;
            });
        }

        function updateMarketingFromChildren() {
            termsMarketingCheckbox.checked = termsMarketingChildCheckboxes.every(function (checkbox) {
                return checkbox.checked;
            });
            updateTermsAll();
        }

        termsAllCheckbox.addEventListener('change', function () {
            termsChildCheckboxes.forEach(function (checkbox) {
                checkbox.checked = termsAllCheckbox.checked;
            });
        });

        if (termsMarketingCheckbox) {
            termsMarketingCheckbox.addEventListener('change', function () {
                termsMarketingChildCheckboxes.forEach(function (checkbox) {
                    checkbox.checked = termsMarketingCheckbox.checked;
                });
                updateTermsAll();
            });
        }

        termsMarketingChildCheckboxes.forEach(function (checkbox) {
            checkbox.addEventListener('change', updateMarketingFromChildren);
        });

        termsChildCheckboxes.forEach(function (checkbox) {
            if (checkbox === termsMarketingCheckbox || termsMarketingChildCheckboxes.indexOf(checkbox) !== -1) {
                return;
            }
            checkbox.addEventListener('change', updateTermsAll);
        });
    }
});
