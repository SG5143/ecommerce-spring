package com.lsg.mingler.domain.payment.service;

import com.lsg.mingler.domain.order.dao.OrderItemRepository;
import com.lsg.mingler.domain.order.dao.OrderRepository;
import com.lsg.mingler.domain.order.entity.Order;
import com.lsg.mingler.domain.order.entity.OrderItem;
import com.lsg.mingler.domain.order.entity.OrderStatus;
import com.lsg.mingler.domain.payment.dao.PaymentRepository;
import com.lsg.mingler.domain.payment.dto.PaymentConfirmResponse;
import com.lsg.mingler.domain.payment.entity.Payment;
import com.lsg.mingler.domain.payment.entity.PaymentStatus;
import com.lsg.mingler.domain.product.dao.ProductOptionRepository;
import com.lsg.mingler.domain.product.dao.ProductRepository;
import com.lsg.mingler.domain.product.entity.Product;
import com.lsg.mingler.domain.product.entity.ProductOption;
import com.lsg.mingler.global.error.ConflictException;
import com.lsg.mingler.global.error.DuplicateException;
import com.lsg.mingler.global.error.ResourceNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentTransactionServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductOptionRepository productOptionRepository;

    @Mock
    private PaymentIdentifierGenerator identifierGenerator;

    @Captor
    private ArgumentCaptor<Payment> paymentCaptor;

    @InjectMocks
    private PaymentTransactionService paymentTransactionService;

    @Test
    void 회원_결제승인은_상품재고를_차감하고_결제와_주문을_완료한다() {
        Order order = memberOrder(500L, 7L, 20_000);
        OrderItem item = orderItem(600L, 500L, 10L, null, 10_000, 2);
        Product product = product(10L, 5);
        stubNewMemberPayment(order, item);
        when(productRepository.findAllByIdInForUpdate(Set.of(10L))).thenReturn(List.of(product));
        stubIdentifiers();

        PaymentConfirmResponse response = paymentTransactionService.confirm(
                7L, null, command(20_000));

        assertThat(product.getStockQuantity()).isEqualTo(3);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(response.orderStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(response.pgProvider()).isEqualTo("VIRTUAL");
        verify(paymentRepository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verify(productOptionRepository, never()).findAllByIdInForUpdate(any());
    }

    @Test
    void 옵션_상품_결제는_옵션재고만_차감한다() {
        Order order = memberOrder(500L, 7L, 20_000);
        OrderItem item = orderItem(600L, 500L, 10L, 20L, 10_000, 2);
        ProductOption option = option(20L, 10L, 4);
        stubNewMemberPayment(order, item);
        when(productOptionRepository.findAllByIdInForUpdate(Set.of(20L))).thenReturn(List.of(option));
        stubIdentifiers();

        paymentTransactionService.confirm(7L, null, command(20_000));

        assertThat(option.getStockQuantity()).isEqualTo(2);
        verify(productRepository, never()).findAllByIdInForUpdate(any());
    }

    @Test
    void 비회원은_주문토큰_해시가_일치하면_결제할_수_있다() {
        Order order = guestOrder(500L, "guest-hash", 10_000);
        OrderItem item = orderItem(600L, 500L, 10L, null, 10_000, 1);
        Product product = product(10L, 2);
        when(orderRepository.findByOrderNumberForUpdate("ORD-1")).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdAndIdempotencyKey(500L, "key-1"))
                .thenReturn(Optional.empty());
        when(orderItemRepository.findAllByOrderIdOrderByIdAsc(500L)).thenReturn(List.of(item));
        when(productRepository.findAllByIdInForUpdate(Set.of(10L))).thenReturn(List.of(product));
        stubIdentifiers();

        PaymentConfirmResponse response = paymentTransactionService.confirm(
                null, "guest-hash", command(10_000));

        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(product.getStockQuantity()).isEqualTo(1);
    }

    @Test
    void 주문_스냅샷과_요청금액이_다르면_재고를_차감하지_않는다() {
        Order order = memberOrder(500L, 7L, 20_000);
        OrderItem item = orderItem(600L, 500L, 10L, null, 10_000, 2);
        stubNewMemberPayment(order, item);

        assertThatThrownBy(() -> paymentTransactionService.confirm(
                7L, null, command(19_000)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("결제 요청금액");

        verify(productRepository, never()).findAllByIdInForUpdate(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void 주문항목의_계산금액이_스냅샷과_다르면_결제를_거부한다() {
        Order order = memberOrder(500L, 7L, 20_000);
        OrderItem item = orderItem(600L, 500L, 10L, null, 10_000, 2);
        ReflectionTestUtils.setField(item, "lineAmount", 19_000);
        stubNewMemberPayment(order, item);

        assertThatThrownBy(() -> paymentTransactionService.confirm(
                7L, null, command(20_000)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("스냅샷 금액");

        verify(productRepository, never()).findAllByIdInForUpdate(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void 잠근_재고가_부족하면_결제를_저장하지_않는다() {
        Order order = memberOrder(500L, 7L, 20_000);
        OrderItem item = orderItem(600L, 500L, 10L, null, 10_000, 2);
        Product product = product(10L, 1);
        stubNewMemberPayment(order, item);
        when(productRepository.findAllByIdInForUpdate(Set.of(10L))).thenReturn(List.of(product));

        assertThatThrownBy(() -> paymentTransactionService.confirm(
                7L, null, command(20_000)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("재고가 부족");

        assertThat(product.getStockQuantity()).isEqualTo(1);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void 동일한_성공_결제요청은_재고차감없이_기존응답을_반환한다() {
        Order order = memberOrder(500L, 7L, 20_000);
        order.changeStatus(OrderStatus.PAID);
        Payment payment = payment(700L, 500L, 7L, 20_000);
        when(orderRepository.findByOrderNumberForUpdate("ORD-1")).thenReturn(Optional.of(order));
        when(paymentRepository.findByMemberIdAndIdempotencyKey(7L, "key-1"))
                .thenReturn(Optional.of(payment));

        PaymentConfirmResponse response = paymentTransactionService.confirm(
                7L, null, command(20_000));

        assertThat(response.paymentNumber()).isEqualTo("PAY-1");
        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verify(orderItemRepository, never()).findAllByOrderIdOrderByIdAsc(any());
        verify(productRepository, never()).findAllByIdInForUpdate(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void 동일한_멱등성키의_금액이_다르면_중복요청으로_거부한다() {
        Order order = memberOrder(500L, 7L, 20_000);
        Payment payment = payment(700L, 500L, 7L, 19_000);
        when(orderRepository.findByOrderNumberForUpdate("ORD-1")).thenReturn(Optional.of(order));
        when(paymentRepository.findByMemberIdAndIdempotencyKey(7L, "key-1"))
                .thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentTransactionService.confirm(
                7L, null, command(20_000)))
                .isInstanceOf(DuplicateException.class)
                .hasMessageContaining("이전 요청과 달라");

        verify(orderItemRepository, never()).findAllByOrderIdOrderByIdAsc(any());
    }

    @Test
    void 다른_회원의_주문은_존재하지_않는_것처럼_처리한다() {
        Order order = memberOrder(500L, 8L, 20_000);
        when(orderRepository.findByOrderNumberForUpdate("ORD-1")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> paymentTransactionService.confirm(
                7L, null, command(20_000)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("주문을 찾을 수 없습니다");

        verify(paymentRepository, never()).findByMemberIdAndIdempotencyKey(any(), any());
    }

    private void stubNewMemberPayment(Order order, OrderItem item) {
        when(orderRepository.findByOrderNumberForUpdate("ORD-1")).thenReturn(Optional.of(order));
        when(paymentRepository.findByMemberIdAndIdempotencyKey(7L, "key-1"))
                .thenReturn(Optional.empty());
        when(orderItemRepository.findAllByOrderIdOrderByIdAsc(500L)).thenReturn(List.of(item));
    }

    private void stubIdentifiers() {
        when(identifierGenerator.generatePaymentNumber()).thenReturn("PAY-1");
        when(paymentRepository.existsByPaymentNumber("PAY-1")).thenReturn(false);
        when(identifierGenerator.generateTransactionKey()).thenReturn("VPG-1");
        when(paymentRepository.existsByPgTransactionKey("VPG-1")).thenReturn(false);
    }

    private PaymentService.PaymentConfirmCommand command(int amount) {
        return new PaymentService.PaymentConfirmCommand("ORD-1", amount, "CARD", "key-1");
    }

    private Order memberOrder(Long id, Long memberId, int amount) {
        Order order = order(memberId, null, amount);
        ReflectionTestUtils.setField(order, "id", id);
        return order;
    }

    private Order guestOrder(Long id, String tokenHash, int amount) {
        Order order = order(null, tokenHash, amount);
        ReflectionTestUtils.setField(order, "id", id);
        return order;
    }

    private Order order(Long memberId, String guestTokenHash, int amount) {
        return Order.builder()
                .orderNumber("ORD-1")
                .memberId(memberId)
                .guestTokenHash(guestTokenHash)
                .ordererName("주문자")
                .ordererPhone("010-1111-2222")
                .receiverName("수령인")
                .receiverPhone("010-1111-2222")
                .zipcode("12345")
                .address("서울")
                .merchandiseAmount(amount)
                .discountAmount(0)
                .shippingFee(0)
                .totalAmount(amount)
                .build();
    }

    private OrderItem orderItem(
            Long id,
            Long orderId,
            Long productId,
            Long optionId,
            int unitPrice,
            int quantity) {
        OrderItem item = OrderItem.builder()
                .orderId(orderId)
                .productId(productId)
                .productOptionId(optionId)
                .productName("상품")
                .categoryId(30L)
                .categoryName("카테고리")
                .unitPrice(unitPrice)
                .quantity(quantity)
                .lineAmount(unitPrice * quantity)
                .build();
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }

    private Product product(Long id, int stock) {
        Product product = Product.builder()
                .categoryId(30L)
                .name("상품")
                .price(10_000)
                .stockQuantity(stock)
                .build();
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }

    private ProductOption option(Long id, Long productId, int stock) {
        ProductOption option = ProductOption.builder()
                .productId(productId)
                .name("옵션")
                .stockQuantity(stock)
                .build();
        ReflectionTestUtils.setField(option, "id", id);
        return option;
    }

    private Payment payment(Long id, Long orderId, Long memberId, int amount) {
        Payment payment = Payment.builder()
                .paymentNumber("PAY-1")
                .orderId(orderId)
                .memberId(memberId)
                .idempotencyKey("key-1")
                .pgProvider("VIRTUAL")
                .paymentMethod("CARD")
                .amount(amount)
                .build();
        ReflectionTestUtils.setField(payment, "id", id);
        payment.recordTransactionKey("VPG-1");
        payment.changeStatus(PaymentStatus.SUCCESS);
        return payment;
    }
}
