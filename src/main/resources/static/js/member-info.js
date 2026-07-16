document.addEventListener('DOMContentLoaded', function () {
    // 탈퇴 확인 모달의 적립금 안내에 사용할 보유 적립금
    let memberPointBalance = 0;
    // 결과 모달을 닫을 때 홈으로 이동해야 하는지(탈퇴 완료 안내용)
    let redirectHomeOnResultClose = false;

    // 회원가입에 입력했던 값들을 조회해 폼에 프리필한다.
    // authFetch 가 401 시 재발급/재시도, 실패 시 /login 리다이렉트를 처리한다.
    window.authFetch('/api/v1/members/me/detail')
        .then(function (response) {
            return response.ok ? response.json() : null;
        })
        .then(function (data) {
            if (!data) {
                return;
            }
            setValue('mi-username', data.username);
            setValue('mi-name', data.name);
            setValue('mi-email', data.email);
            setValue('mi-zipcode', data.zipcode);
            setValue('mi-address', data.address);
            setValue('mi-address-detail', data.addressDetail);

            fillPhone(data.phone);
            fillBirthDate(data.birthDate);

            checkRadio('marketingAgreed', data.marketingAgreed);
            checkRadio('emailAgreed', data.emailAgreed);
            checkRadio('smsAgreed', data.smsAgreed);

            memberPointBalance = data.pointBalance || 0;

            // 채널 라디오를 세팅한 직후 마케팅 미동의면 잠금 상태를 반영(이메일·SMS를 수신안함으로 강제)
            applyMarketingGate();
        })
        .catch(function () {
            // 조회 실패 시 빈 폼 유지
        });

    // 마케팅 수신동의(상위)가 '동의안함'이면 이메일·SMS 수신여부(하위 채널)를
    // '수신안함'으로 강제하고 선택 불가로 잠금. '동의함'이면 다시 선택 가능하게 품
    function applyMarketingGate() {
        const marketingChecked = document.querySelector('input[name="marketingAgreed"]:checked');
        const agreed = !!marketingChecked && marketingChecked.value === 'true';

        if (!agreed) {
            const emailNo = document.getElementById('mi-email-agreed-no');
            const smsNo = document.getElementById('mi-sms-agreed-no');
            if (emailNo) {
                emailNo.checked = true;
            }
            if (smsNo) {
                smsNo.checked = true;
            }
        }

        document.querySelectorAll('input[name="emailAgreed"], input[name="smsAgreed"]').forEach(function (radio) {
            radio.disabled = !agreed;
        });
        document.querySelectorAll('.channel-agreed-group').forEach(function (group) {
            group.classList.toggle('is-locked', !agreed);
        });
    }

    // 마케팅 동의 라디오 변경 시 채널 잠금 상태를 갱신
    document.querySelectorAll('input[name="marketingAgreed"]').forEach(function (radio) {
        radio.addEventListener('change', applyMarketingGate);
    });

    // 조회 실패(빈 폼, 마케팅 미선택) 시에도 초기부터 잠금 상태가 되도록 한 번 호출.
    applyMarketingGate();

    function setValue(id, value) {
        const el = document.getElementById(id);
        if (el && value != null) {
            el.value = value;
        }
    }

    // 저장된 휴대폰(숫자만)을 3칸으로 분해 (10자리: 3-3-4, 11자리: 3-4-4)
    function fillPhone(phone) {
        const digits = (phone || '').replace(/[^0-9]/g, '');
        if (digits.length < 10) {
            return;
        }
        setValue('mi-phone1', digits.slice(0, 3));
        setValue('mi-phone2', digits.slice(3, digits.length - 4));
        setValue('mi-phone3', digits.slice(-4));
    }

    // ISO 문자열(yyyy-MM-dd)을 연·월·일 3칸으로 분해
    function fillBirthDate(birthDate) {
        if (!birthDate) {
            return;
        }
        const parts = birthDate.split('-');
        setValue('mi-birth-year', parts[0]);
        setValue('mi-birth-month', parts[1]);
        setValue('mi-birth-day', parts[2]);
    }

    // 라디오 그룹에서 boolean 값(true/false)에 해당하는 항목을 체크
    function checkRadio(name, value) {
        const target = document.querySelector('input[name="' + name + '"][value="' + (value ? 'true' : 'false') + '"]');
        if (target) {
            target.checked = true;
        }
    }

    // ---- 휴대폰 번호수정 모달 (회원가입과 동일: 임시 등록) ----
    const phoneVerifyButton = document.getElementById('mi-phone-verify-button');
    const phoneVerifyOverlay = document.getElementById('mi-phone-verify-overlay');
    const phoneVerifyClose = document.getElementById('mi-phone-verify-close');
    const phoneVerifyInput = document.getElementById('mi-phone-verify-input');
    const phoneVerifyConfirm = document.getElementById('mi-phone-verify-confirm');

    if (phoneVerifyButton && phoneVerifyOverlay && phoneVerifyClose && phoneVerifyInput && phoneVerifyConfirm) {
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
        ['mi-phone1', 'mi-phone2', 'mi-phone3'].forEach(function (id) {
            const el = document.getElementById(id);
            if (el) {
                el.addEventListener('click', openPhoneVerifyModal);
            }
        });
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
            const digits = phoneVerifyInput.value.replace(/[^0-9]/g, '');
            if (digits.length < 10) {
                return;
            }
            fillPhone(digits);
            closePhoneVerifyModal();
        });
    }

    // ---- 우편번호 검색 모달 (다음 우편번호 API) ----
    const zipcodeSearchButton = document.getElementById('mi-zipcode-search-button');
    const zipcodeInput = document.getElementById('mi-zipcode');
    const addressInput = document.getElementById('mi-address');
    const addressDetailInput = document.getElementById('mi-address-detail');
    const zipcodeOverlay = document.getElementById('mi-zipcode-search-overlay');
    const zipcodeClose = document.getElementById('mi-zipcode-search-close');
    const zipcodeEmbedTarget = document.getElementById('mi-zipcode-search-embed');

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

    // ---- 저장(회원정보 수정 + 선택적 비밀번호 변경) ----
    // 프론트 검증 규칙은 signup.js / MemberService 와 대칭을 유지한다.
    const MINIMUM_AGE = 14;
    const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    const EMAIL_MAX_LENGTH = 255;
    const PASSWORD_ALLOWED_PATTERN = /^[a-zA-Z0-9!@#$%^*+=.-]{8,20}$/;
    const PASSWORD_CHAR_GROUPS = [/[a-zA-Z]/, /[0-9]/, /[!@#$%^*+=.-]/];

    function getValue(id) {
        const el = document.getElementById(id);
        return el ? el.value.trim() : '';
    }

    function isRadioTrue(name) {
        const checked = document.querySelector('input[name="' + name + '"]:checked');
        return !!checked && checked.value === 'true';
    }

    function hasRadioSelection(name) {
        return !!document.querySelector('input[name="' + name + '"]:checked');
    }

    function isPasswordValid(value) {
        if (!PASSWORD_ALLOWED_PATTERN.test(value)) {
            return false;
        }
        return PASSWORD_CHAR_GROUPS.filter(function (group) {
            return group.test(value);
        }).length >= 2;
    }

    // 연·월·일 3칸을 Date 로 재조합해 유효 날짜(롤오버)만 통과
    function getBirthdate() {
        const year = Number(getValue('mi-birth-year'));
        const month = Number(getValue('mi-birth-month'));
        const day = Number(getValue('mi-birth-day'));
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

    // 비밀번호 3칸 중 하나라도 입력되면 변경 시도로 간주
    function isPasswordChangeRequested() {
        return !!(getValue('mi-current-password') || getValue('mi-new-password') || getValue('mi-new-password-confirm'));
    }

    function firstError() {
        if (!getValue('mi-name')) {
            return '이름을 입력해주세요.';
        }

        const email = getValue('mi-email');
        if (email && (email.length > EMAIL_MAX_LENGTH || !EMAIL_PATTERN.test(email))) {
            return '이메일 형식을 확인해주세요.';
        }

        const phone = getValue('mi-phone1') + getValue('mi-phone2') + getValue('mi-phone3');
        if (phone.length < 10) {
            return '휴대폰 번호를 입력해주세요.';
        }

        const birthdate = getBirthdate();
        if (!birthdate) {
            return '생년월일을 정확히 선택해주세요.';
        }
        if (birthdate.getTime() > Date.now()) {
            return '생년월일은 오늘까지만 입력할 수 있습니다.';
        }
        if (!isAtLeastMinimumAge(birthdate)) {
            return '만 14세 이상만 이용할 수 있습니다.';
        }

        if (!hasRadioSelection('marketingAgreed')) {
            return '마케팅 수신동의 여부를 선택해주세요.';
        }

        if (isPasswordChangeRequested()) {
            const current = getValue('mi-current-password');
            if (!current) {
                return '현재 비밀번호를 입력해주세요.';
            }
            const newPassword = getValue('mi-new-password');
            if (!newPassword) {
                return '새 비밀번호를 입력해주세요.';
            }
            if (!isPasswordValid(newPassword)) {
                return '비밀번호 형식을 확인해주세요.';
            }
            if (newPassword !== getValue('mi-new-password-confirm')) {
                return '비밀번호가 일치하지 않습니다.';
            }
            if (current === newPassword) {
                return '새 비밀번호가 현재 비밀번호와 같습니다.';
            }
        }

        return null;
    }

    function buildProfilePayload() {
        return {
            name: getValue('mi-name'),
            phone: getValue('mi-phone1') + getValue('mi-phone2') + getValue('mi-phone3'),
            email: getValue('mi-email'),
            zipcode: getValue('mi-zipcode'),
            address: getValue('mi-address'),
            addressDetail: getValue('mi-address-detail'),
            birthYear: Number(getValue('mi-birth-year')),
            birthMonth: Number(getValue('mi-birth-month')),
            birthDay: Number(getValue('mi-birth-day')),
            marketingAgreed: isRadioTrue('marketingAgreed'),
            emailAgreed: isRadioTrue('emailAgreed'),
            smsAgreed: isRadioTrue('smsAgreed')
        };
    }

    function buildPasswordPayload() {
        return {
            currentPassword: getValue('mi-current-password'),
            newPassword: getValue('mi-new-password'),
            newPasswordConfirm: getValue('mi-new-password-confirm')
        };
    }

    // authFetch(Bearer 부착 + 401 재발급) 로 JSON 요청을 보내고 {ok, data} 로 정규화
    function requestJson(url, method, payload) {
        return window.authFetch(url, {
            method: method,
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        }).then(function (response) {
            return response.json().catch(function () {
                return {};
            }).then(function (data) {
                return { ok: response.ok, data: data };
            });
        });
    }

    // ---- 결과 알림 모달(회원가입 페이지와 동일한 modal-overlay 스타일 재사용) ----
    const resultOverlay = document.getElementById('mi-result-overlay');
    const resultClose = document.getElementById('mi-result-close');
    const resultTitle = document.getElementById('mi-result-title');
    const resultMessage = document.getElementById('mi-result-message');

    function openResultModal(title, message) {
        if (!resultOverlay) {
            window.alert(message);
            return;
        }
        resultTitle.textContent = title;
        resultMessage.textContent = message;
        resultOverlay.classList.add('is-open');
    }

    function closeResultModal() {
        if (resultOverlay) {
            resultOverlay.classList.remove('is-open');
        }
        if (redirectHomeOnResultClose) {
            window.location.href = '/';
        }
    }

    if (resultOverlay && resultClose) {
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
    }

    // ---- 입력 중 폼 아래에 실시간 안내(에러) 표시 ----
    function setError(id, text) {
        const el = document.getElementById(id);
        if (el) {
            el.textContent = text || '';
        }
    }

    const emailInput = document.getElementById('mi-email');
    if (emailInput) {
        emailInput.addEventListener('input', function () {
            const value = emailInput.value.trim();
            const invalid = value && (value.length > EMAIL_MAX_LENGTH || !EMAIL_PATTERN.test(value));
            setError('mi-email-error', invalid ? '이메일 형식에 맞게 입력해주세요.' : '');
        });
    }

    function validateBirthInline() {
        // 세 칸이 모두 채워지기 전에는 안내를 띄우지 않음
        if (!getValue('mi-birth-year') || !getValue('mi-birth-month') || !getValue('mi-birth-day')) {
            setError('mi-birth-error', '');
            return;
        }
        const birthdate = getBirthdate();
        if (!birthdate) {
            setError('mi-birth-error', '생년월일을 정확히 선택해주세요.');
        } else if (birthdate.getTime() > Date.now()) {
            setError('mi-birth-error', '생년월일은 오늘까지만 입력할 수 있습니다.');
        } else if (!isAtLeastMinimumAge(birthdate)) {
            setError('mi-birth-error', '만 14세 이상만 이용할 수 있습니다.');
        } else {
            setError('mi-birth-error', '');
        }
    }

    ['mi-birth-year', 'mi-birth-month', 'mi-birth-day'].forEach(function (id) {
        const el = document.getElementById(id);
        if (el) {
            el.addEventListener('input', validateBirthInline);
        }
    });

    const newPasswordInput = document.getElementById('mi-new-password');
    const newPasswordConfirmInput = document.getElementById('mi-new-password-confirm');

    function validatePasswordConfirmInline() {
        const newPassword = getValue('mi-new-password');
        const confirm = getValue('mi-new-password-confirm');
        const mismatched = confirm && newPassword !== confirm;
        setError('mi-new-password-confirm-error', mismatched ? '비밀번호가 일치하지 않습니다.' : '');
    }

    if (newPasswordInput) {
        newPasswordInput.addEventListener('input', function () {
            const value = getValue('mi-new-password');
            const invalid = value && !isPasswordValid(value);
            setError('mi-new-password-error', invalid ? '영문, 숫자, 특수문자(!@#$%^*+=.-) 중 2가지 이상을 조합한 8~20자여야 합니다.' : '');
            validatePasswordConfirmInline();
        });
    }
    if (newPasswordConfirmInput) {
        newPasswordConfirmInput.addEventListener('input', validatePasswordConfirmInline);
    }

    const submitButton = document.getElementById('mi-submit');
    if (submitButton) {
        submitButton.addEventListener('click', function () {
            const error = firstError();
            if (error) {
                openResultModal('입력 정보를 확인해주세요', error);
                return;
            }

            submitButton.disabled = true;
            const changePassword = isPasswordChangeRequested();

            requestJson('/api/v1/members/me', 'PUT', buildProfilePayload())
                .then(function (result) {
                    if (!result.ok) {
                        throw new Error((result.data && result.data.message) || '회원정보 수정에 실패했습니다.');
                    }
                    if (!changePassword) {
                        return null;
                    }
                    return requestJson('/api/v1/members/me/password', 'PATCH', buildPasswordPayload())
                        .then(function (pwResult) {
                            if (!pwResult.ok) {
                                throw new Error((pwResult.data && pwResult.data.message) || '비밀번호 변경에 실패했습니다.');
                            }
                            return null;
                        });
                })
                .then(function () {
                    if (changePassword) {
                        // 비밀번호 변경 시 서버가 모든 Refresh 토큰을 폐기하므로 재로그인이 필요.
                        openResultModal('비밀번호 변경 완료', '보안을 위해 다시 로그인해주세요.');
                        setTimeout(function () {
                            window.clearAccessToken();
                            window.location.href = '/login';
                        }, 3000);
                        return;
                    }
                    openResultModal('저장 완료', '회원정보가 저장되었습니다.');
                    setTimeout(function () {
                        window.location.reload();
                    }, 1000);
                })
                .catch(function (err) {
                    submitButton.disabled = false;
                    openResultModal('저장 실패', err && err.message ? err.message : '처리 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.');
                });
        });
    }

    // ---- 회원 탈퇴 ----
    const withdrawButton = document.getElementById('mi-withdraw');
    const withdrawOverlay = document.getElementById('mi-withdraw-overlay');
    const withdrawClose = document.getElementById('mi-withdraw-close');
    const withdrawCancel = document.getElementById('mi-withdraw-cancel');
    const withdrawConfirm = document.getElementById('mi-withdraw-confirm');
    const withdrawPoints = document.getElementById('mi-withdraw-points');

    if (withdrawButton && withdrawOverlay && withdrawConfirm) {
        function openWithdrawModal() {
            if (withdrawPoints) {
                withdrawPoints.textContent = Number(memberPointBalance || 0).toLocaleString('ko-KR') + '원';
            }
            withdrawOverlay.classList.add('is-open');
        }

        function closeWithdrawModal() {
            withdrawOverlay.classList.remove('is-open');
        }

        withdrawButton.addEventListener('click', openWithdrawModal);
        if (withdrawClose) {
            withdrawClose.addEventListener('click', closeWithdrawModal);
        }
        if (withdrawCancel) {
            withdrawCancel.addEventListener('click', closeWithdrawModal);
        }
        withdrawOverlay.addEventListener('click', function (event) {
            if (event.target === withdrawOverlay) {
                closeWithdrawModal();
            }
        });
        document.addEventListener('keydown', function (event) {
            if (event.key === 'Escape') {
                closeWithdrawModal();
            }
        });

        withdrawConfirm.addEventListener('click', function () {
            withdrawConfirm.disabled = true;
            window.authFetch('/api/v1/members/me', { method: 'DELETE' })
                .then(function (response) {
                    if (!response.ok) {
                        throw new Error('회원 탈퇴에 실패했습니다. 잠시 후 다시 시도해주세요.');
                    }
                    // 탈퇴 완료: 서버가 모든 Refresh 토큰을 폐기 → 토큰 삭제 후 완료 모달을 띄우고,
                    // 사용자가 모달을 닫으면 홈으로 이동한다.
                    window.clearAccessToken();
                    closeWithdrawModal();
                    redirectHomeOnResultClose = true;
                    openResultModal('회원 탈퇴 완료', '회원 탈퇴가 완료되었습니다.');
                })
                .catch(function (err) {
                    withdrawConfirm.disabled = false;
                    closeWithdrawModal();
                    openResultModal('탈퇴 실패', err && err.message ? err.message : '회원 탈퇴에 실패했습니다.');
                });
        });
    }
});
