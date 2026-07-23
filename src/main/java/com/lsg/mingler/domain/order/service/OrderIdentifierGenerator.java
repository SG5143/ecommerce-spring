package com.lsg.mingler.domain.order.service;

import com.lsg.mingler.global.util.HashUtils;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

@Component
public class OrderIdentifierGenerator {

    private static final int ORDER_NUMBER_BYTES = 8;
    private static final int GUEST_TOKEN_BYTES = 32;
    private static final DateTimeFormatter ORDER_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 고객 문의와 주문 조회에 노출할 주문번호를 생성한다.
     * 날짜 뒤에 64비트 난수를 붙이고 최종 중복 여부는 서비스와 DB UNIQUE 제약이 확인한다.
     */
    public String generateOrderNumber() {
        byte[] randomBytes = new byte[ORDER_NUMBER_BYTES];
        secureRandom.nextBytes(randomBytes);
        return "ORD-" + LocalDate.now().format(ORDER_DATE_FORMAT) + "-"
                + HexFormat.of().withUpperCase().formatHex(randomBytes);
    }

    /**
     * 비회원에게 한 번만 전달할 조회 토큰과 DB 저장용 SHA-256 해시를 함께 생성한다.
     * 원문을 저장하지 않아 DB 노출 시 토큰 원문이 바로 재사용되는 것을 방지한다.
     */
    public GuestToken generateGuestToken() {
        byte[] randomBytes = new byte[GUEST_TOKEN_BYTES];
        secureRandom.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        return new GuestToken(rawToken, HashUtils.sha256Hex(rawToken));
    }

    public record GuestToken(String rawToken, String hash) {}

}
