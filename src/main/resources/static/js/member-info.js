document.addEventListener('DOMContentLoaded', function () {
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
            setValue('mi-zipcode', data.zipcode);
            setValue('mi-address', data.address);
            setValue('mi-address-detail', data.addressDetail);

            fillPhone(data.phone);
            fillBirthDate(data.birthDate);

            const marketingRadio = document.getElementById(data.marketingAgreed ? 'mi-marketing-yes' : 'mi-marketing-no');
            if (marketingRadio) {
                marketingRadio.checked = true;
            }
        })
        .catch(function () {
            // 조회 실패 시 빈 폼 유지
        });

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
});
