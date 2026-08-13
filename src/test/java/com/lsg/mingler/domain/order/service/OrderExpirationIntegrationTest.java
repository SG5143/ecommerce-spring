package com.lsg.mingler.domain.order.service;

import com.lsg.mingler.domain.member.dao.MemberRepository;
import com.lsg.mingler.domain.member.entity.Member;
import com.lsg.mingler.domain.order.dao.OrderRepository;
import com.lsg.mingler.domain.order.dto.OrderHistoryResponse;
import com.lsg.mingler.domain.order.entity.Order;
import com.lsg.mingler.domain.order.entity.OrderStatus;
import com.lsg.mingler.domain.payment.dao.PaymentRepository;
import com.lsg.mingler.domain.payment.entity.Payment;
import com.lsg.mingler.domain.payment.entity.PaymentStatus;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OrderExpirationIntegrationTest {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OrderExpirationTransactionService transactionService;

    @Autowired
    private OrderHistoryService orderHistoryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private Long paymentId;
    private Long orderId;
    private Long memberId;

    @AfterEach
    void 정리한다() {
        if (paymentId != null) {
            paymentRepository.deleteById(paymentId);
        }
        if (orderId != null) {
            orderRepository.deleteById(orderId);
        }
        if (memberId != null) {
            memberRepository.deleteById(memberId);
        }
    }

    @Test
    void 오래된_결제준비와_주문을_함께_만료하고_회원_주문내역에서_제외한다() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minusMinutes(30);
        LocalDateTime oldCreatedAt = cutoff.minusMinutes(1);
        Member member = memberRepository.save(Member.builder()
                .username("expire-" + suffix)
                .password("encoded-password")
                .name("만료 테스트")
                .phone("010" + suffix)
                .birthDate(LocalDate.of(1990, 1, 1))
                .marketingAgreed(false)
                .termsAgreedAt(now)
                .privacyAgreedAt(now)
                .build());
        memberId = member.getId();
        Order order = orderRepository.saveAndFlush(Order.builder()
                .orderNumber("ORD-EXPIRE-" + suffix)
                .memberId(memberId)
                .ordererName("주문자")
                .ordererPhone("01012345678")
                .receiverName("수령인")
                .receiverPhone("01012345678")
                .zipcode("12345")
                .address("서울")
                .merchandiseAmount(10_000)
                .totalAmount(10_000)
                .build());
        orderId = order.getId();
        Payment payment = Payment.builder()
                .paymentNumber("PAY-EXPIRE-" + suffix)
                .orderId(orderId)
                .memberId(memberId)
                .idempotencyKey("key-" + suffix)
                .pgProvider("VIRTUAL")
                .paymentMethod("CARD")
                .amount(10_000)
                .build();
        payment.recordPreparation("PG-EXPIRE-" + suffix);
        payment = paymentRepository.saveAndFlush(payment);
        paymentId = payment.getId();
        jdbcTemplate.update("UPDATE orders SET created_at = ? WHERE id = ?", oldCreatedAt, orderId);
        jdbcTemplate.update("UPDATE payment SET created_at = ? WHERE id = ?", oldCreatedAt, paymentId);
        entityManager.clear();

        List<Long> candidates = orderRepository.findExpirationCandidateIds(
                OrderStatus.PENDING_PAYMENT,
                cutoff,
                PaymentStatus.PENDING,
                cutoff,
                EnumSet.of(
                        PaymentStatus.PROCESSING,
                        PaymentStatus.SUCCESS,
                        PaymentStatus.REFUND_PENDING,
                        PaymentStatus.REFUNDED,
                        PaymentStatus.REFUND_FAILED),
                PageRequest.of(0, 100));
        assertThat(candidates).contains(orderId);

        assertThat(transactionService.expire(orderId, cutoff, cutoff)).isTrue();

        Order expiredOrder = orderRepository.findById(orderId).orElseThrow();
        Payment cancelledPayment = paymentRepository.findById(paymentId).orElseThrow();
        assertThat(expiredOrder.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(expiredOrder.getExpiredAt()).isNotNull();
        assertThat(cancelledPayment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(cancelledPayment.getFailureCode()).isEqualTo("ORDER_EXPIRED");

        OrderHistoryResponse history = orderHistoryService.getHistory(memberId, 0);
        assertThat(history.orders()).isEmpty();
        assertThat(history.totalElements()).isZero();
    }
}
