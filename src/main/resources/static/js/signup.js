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
    let isUsernameAvailable = false;

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
                    isUsernameAvailable = data.passed;
                    usernameMessage = setFieldMessage(usernameInput, usernameMessage, data.msg, !data.passed);
                })
                .catch(function () {
                    isUsernameAvailable = false;
                    usernameMessage = setFieldMessage(usernameInput, usernameMessage, '아이디 확인 중 오류가 발생했습니다.', true);
                });
        }

        usernameInput.addEventListener('input', function () {
            isUsernameAvailable = false;
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

    const signupForm = document.querySelector('.signup-form');
    const resultOverlay = document.getElementById('signup-result-overlay');
    const resultClose = document.getElementById('signup-result-close');
    const resultTitle = document.getElementById('signup-result-title');
    const resultMessage = document.getElementById('signup-result-message');

    if (signupForm && resultOverlay && resultClose && resultTitle && resultMessage) {
        function openResultModal(title, message) {
            resultTitle.textContent = title;
            resultMessage.textContent = message;
            resultOverlay.classList.add('is-open');
        }

        function closeResultModal() {
            resultOverlay.classList.remove('is-open');
        }

        resultClose.addEventListener('click', closeResultModal);
        resultOverlay.addEventListener('click', function (event) {
            if (event.target === resultOverlay) {
                closeResultModal();
            }
        });
        document.addEventListener('keydown', function (event) {
            if (event.key === 'Escape') {
                closeResultModal();
            }
        });

        const MINIMUM_AGE = 14;

        function getValue(id) {
            const el = document.getElementById(id);
            return el ? el.value.trim() : '';
        }

        function getBirthdate() {
            const year = Number(signupForm.elements['birthYear'].value);
            const month = Number(signupForm.elements['birthMonth'].value);
            const day = Number(signupForm.elements['birthDay'].value);

            if (!year || !month || !day) {
                return null;
            }

            const date = new Date(year, month - 1, day);
            if (date.getFullYear() !== year || date.getMonth() !== month - 1 || date.getDate() !== day) {
                return null;
            }
            return date;
        }

        function isAtLeastMinimumAge(birthdate) {
            const today = new Date();
            let age = today.getFullYear() - birthdate.getFullYear();
            const monthDiff = today.getMonth() - birthdate.getMonth();
            if (monthDiff < 0 || (monthDiff === 0 && today.getDate() < birthdate.getDate())) {
                age -= 1;
            }
            return age >= MINIMUM_AGE;
        }

        function firstError() {
            if (!getValue('signup-username')) {
                return '아이디를 입력해주세요.';
            }
            if (!isUsernameAvailable) {
                return '중복된 아이디입니다.';
            }

            const password = getValue('signup-password');
            if (!password) {
                return '비밀번호를 입력해주세요.';
            }
            if (!isPasswordValid(password)) {
                return '비밀번호 형식을 확인해주세요.';
            }

            const passwordConfirm = getValue('signup-password-confirm');
            if (!passwordConfirm) {
                return '비밀번호 확인을 입력해주세요.';
            }
            if (password !== passwordConfirm) {
                return '비밀번호가 일치하지 않습니다.';
            }

            if (!getValue('signup-name')) {
                return '이름을 입력해주세요.';
            }
            if (!getValue('signup-phone')) {
                return '핸드폰 본인확인을 진행해주세요.';
            }
            if (!getValue('signup-zipcode')) {
                return '우편번호를 입력해주세요.';
            }
            if (!getValue('signup-address')) {
                return '주소를 입력해주세요.';
            }

            const birthdate = getBirthdate();
            if (!birthdate) {
                return '생년월일을 정확히 선택해주세요.';
            }
            if (!isAtLeastMinimumAge(birthdate)) {
                return '만 14세 이상만 가입할 수 있습니다.';
            }

            const requiredTerms = Array.from(document.querySelectorAll('[data-terms-group="required"]'));
            const allTermsAgreed = requiredTerms.length > 0 && requiredTerms.every(function (checkbox) {
                return checkbox.checked;
            });
            if (!allTermsAgreed) {
                return '필수 약관에 모두 동의해주세요.';
            }

            return null;
        }

        signupForm.addEventListener('submit', function (event) {
            event.preventDefault();
            event.stopImmediatePropagation();

            const error = firstError();
            if (error) {
                openResultModal('입력 정보를 확인해주세요', error);
                return;
            }

            openResultModal('입력 완료', '모든 항목이 정상적으로 입력되었습니다.');
        });
    }
});
