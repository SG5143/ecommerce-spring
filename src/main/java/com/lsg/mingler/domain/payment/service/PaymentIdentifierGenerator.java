package com.lsg.mingler.domain.payment.service;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PaymentIdentifierGenerator {

    private static final int PAYMENT_NUMBER_BYTES = 8;
    private static final int TRANSACTION_KEY_BYTES = 12;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 날짜와 암호학적 난수로 결제번호 후보를 생성한다.
     *
     * @return {@code PAY-yyyyMMdd-난수} 형식의 결제번호
     */
    public String generatePaymentNumber() {
        return "PAY-" + LocalDate.now().format(DATE_FORMAT) + "-" + randomHex(PAYMENT_NUMBER_BYTES);
    }

    /**
     * 가상 PG 승인에 사용할 거래키 후보를 생성한다.
     *
     * @return {@code VPG-난수} 형식의 PG 거래키
     */
    public String generateTransactionKey() {
        return "VPG-" + randomHex(TRANSACTION_KEY_BYTES);
    }

    public String generatePgOrderId(String provider) {
        return provider + "-" + UUID.randomUUID();
    }

    public String customerKeyFrom(String pgOrderId) {
        int separator = pgOrderId.indexOf('-');
        String randomPart = separator >= 0 ? pgOrderId.substring(separator + 1) : pgOrderId;
        return "customer-" + randomPart;
    }

    /**
     * 지정한 바이트 길이만큼 보안 난수를 생성해 대문자 16진수 문자열로 변환한다.
     *
     * @param byteLength 생성할 난수의 바이트 길이
     * @return 대문자 16진수 난수 문자열
     */
    private String randomHex(int byteLength) {
        byte[] bytes = new byte[byteLength];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().withUpperCase().formatHex(bytes);
    }
}
