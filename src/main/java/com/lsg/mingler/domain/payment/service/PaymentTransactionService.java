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
import com.lsg.mingler.global.error.ResourceNotFoundException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentTransactionService {

    private static final int IDENTIFIER_GENERATION_ATTEMPTS = 5;
    private static final String PG_PROVIDER = "VIRTUAL";

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final ProductRepository productRepository;
    private final ProductOptionRepository productOptionRepository;
    private final PaymentIdentifierGenerator identifierGenerator;
    private final VirtualPaymentGateway virtualPaymentGateway;

    /**
     * 주문과 재고를 잠근 단일 트랜잭션에서 금액 검증, 재고 차감, 결제 승인을 완료한다.
     *
     * @param memberId 인증된 회원 ID이며 비회원 요청이면 {@code null}
     * @param guestOrderTokenHash 비회원 주문 토큰 해시이며 회원 요청이면 {@code null}
     * @param command 검증과 정규화가 완료된 결제 승인 명령
     * @return 승인된 결제와 주문 상태
     */
    @Transactional
    PaymentConfirmResponse confirm(Long memberId, String guestOrderTokenHash, PaymentService.PaymentConfirmCommand command) {
        Order order = orderRepository.findByOrderNumberForUpdate(command.orderNumber()).orElseThrow(()
                -> new ResourceNotFoundException("주문을 찾을 수 없습니다."));
        PaymentOrderOwnershipPolicy.validate(order, memberId, guestOrderTokenHash);

        Optional<Payment> existing = findExistingPayment(order.getId(), memberId, command.idempotencyKey());
        if (existing.isPresent()) {
            return replayExisting(order, existing.get(), command);
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new ConflictException("결제할 수 있는 주문 상태가 아닙니다.");
        }

        List<OrderItem> orderItems = orderItemRepository.findAllByOrderIdOrderByIdAsc(order.getId());
        validateSnapshotAmount(order, orderItems, command.amount());
        decreaseStocks(orderItems);
        virtualPaymentGateway.approve(command.scenario());

        Payment payment = Payment.builder()
                .paymentNumber(generatePaymentNumber())
                .orderId(order.getId())
                .memberId(memberId)
                .idempotencyKey(command.idempotencyKey())
                .pgProvider(PG_PROVIDER)
                .paymentMethod(command.paymentMethod())
                .amount(command.amount())
                .build();
        payment.recordTransactionKey(generateTransactionKey());
        payment.changeStatus(PaymentStatus.SUCCESS);
        order.changeStatus(OrderStatus.PAID);
        paymentRepository.save(payment);

        return toResponse(order, payment);
    }

    /**
     * 요청 소유자 범위에서 같은 멱등성 키로 저장된 결제를 조회한다.
     *
     * @param orderId 비회원 결제를 구분할 주문 ID
     * @param memberId 회원 결제를 구분할 회원 ID
     * @param idempotencyKey 결제 요청 멱등성 키
     * @return 같은 멱등성 키로 저장된 결제, 없으면 빈 값
     */
    private Optional<Payment> findExistingPayment(Long orderId, Long memberId, String idempotencyKey) {
        if (memberId != null) {
            return paymentRepository.findByMemberIdAndIdempotencyKey(memberId, idempotencyKey);
        }
        return paymentRepository.findByOrderIdAndIdempotencyKey(orderId, idempotencyKey);
    }

    /**
     * 기존 결제가 현재 요청과 동일하고 승인 완료 상태이면 저장된 결과를 재응답한다.
     *
     * @param order 결제 대상 주문
     * @param payment 같은 멱등성 키로 저장된 결제
     * @param command 현재 결제 승인 명령
     * @return 기존 승인 결과
     * @throws DuplicateException 기존 결제와 현재 요청 내용이 다른 경우
     */
    private PaymentConfirmResponse replayExisting(Order order, Payment payment, PaymentService.PaymentConfirmCommand command) {
        PaymentRequestPolicy.validateSameRequest(order, payment, command);
        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new ConflictException("동일한 결제 요청이 처리 중이거나 완료되지 않았습니다.");
        }
        return PaymentConfirmResponseMapper.from(order, payment);
    }

    /**
     * 주문 상품 스냅샷의 행별 금액과 합계를 재계산해 주문·요청 금액과 일치하는지 검증한다.
     * 정확한 정수 연산을 사용해 계산 중 오버플로도 결제 충돌로 처리한다.
     *
     * @param order 결제 대상 주문
     * @param orderItems 주문 당시 저장된 상품 스냅샷
     * @param requestedAmount 클라이언트가 요청한 결제금액
     */
    private void validateSnapshotAmount(Order order, List<OrderItem> orderItems, Integer requestedAmount) {
        if (orderItems.isEmpty()) {
            throw new ConflictException("주문 상품 스냅샷이 없습니다.");
        }

        int merchandiseAmount = 0;
        try {
            for (OrderItem item : orderItems) {
                if (item.getUnitPrice() == null || item.getUnitPrice() < 0
                        || item.getQuantity() == null || item.getQuantity() <= 0
                        || item.getLineAmount() == null) {
                    throw new ConflictException("주문 상품 스냅샷 금액이 올바르지 않습니다.");
                }
                int lineAmount = Math.multiplyExact(item.getUnitPrice(), item.getQuantity());
                if (lineAmount != item.getLineAmount()) {
                    throw new ConflictException("주문 상품 스냅샷 금액이 일치하지 않습니다.");
                }
                merchandiseAmount = Math.addExact(merchandiseAmount, lineAmount);
            }

            if (merchandiseAmount != order.getMerchandiseAmount()) {
                throw new ConflictException("주문 상품금액 합계가 일치하지 않습니다.");
            }
            int totalAmount = Math.addExact(
                    Math.subtractExact(order.getMerchandiseAmount(), order.getDiscountAmount()),
                    order.getShippingFee());
            if (totalAmount != order.getTotalAmount() || totalAmount != requestedAmount) {
                throw new ConflictException("결제 요청금액이 주문금액과 일치하지 않습니다.");
            }
        } catch (ArithmeticException e) {
            throw new ConflictException("주문금액이 허용 범위를 초과했습니다.");
        }
    }

    /**
     * 주문 상품별 수량을 합산한 뒤 재고 행을 일정한 순서로 잠그고 차감한다.
     * 일반 상품 재고와 옵션 재고는 각각 해당 엔티티에서 차감한다.
     *
     * @param orderItems 재고를 차감할 주문 상품 스냅샷
     */
    private void decreaseStocks(List<OrderItem> orderItems) {
        TreeMap<Long, Integer> productQuantities = new TreeMap<>();
        TreeMap<Long, OptionQuantity> optionQuantities = new TreeMap<>();
        for (OrderItem item : orderItems) {
            if (item.getProductOptionId() == null) {
                productQuantities.merge(item.getProductId(), item.getQuantity(), this::safeAddQuantity);
                continue;
            }
            optionQuantities.merge(
                    item.getProductOptionId(),
                    new OptionQuantity(item.getProductId(), item.getQuantity()),
                    this::mergeOptionQuantity);
        }

        Map<Long, Product> products = productQuantities.isEmpty()
                ? Map.of()
                : toIdMap(productRepository.findAllByIdInForUpdate(productQuantities.keySet()), Product::getId);
        Map<Long, ProductOption> options = optionQuantities.isEmpty()
                ? Map.of()
                : toIdMap(productOptionRepository.findAllByIdInForUpdate(optionQuantities.keySet()), ProductOption::getId);

        for (Map.Entry<Long, Integer> entry : productQuantities.entrySet()) {
            Product product = products.get(entry.getKey());
            if (product == null) {
                throw new ConflictException("상품 재고 정보를 찾을 수 없습니다.");
            }
            if (product.getStockQuantity() < entry.getValue()) {
                throw new ConflictException("재고가 부족한 상품이 포함되어 있습니다.");
            }
            product.decreaseStock(entry.getValue());
        }
        for (Map.Entry<Long, OptionQuantity> entry : optionQuantities.entrySet()) {
            ProductOption option = options.get(entry.getKey());
            OptionQuantity quantity = entry.getValue();
            if (option == null || !quantity.productId().equals(option.getProductId())) {
                throw new ConflictException("상품 옵션 재고 정보를 찾을 수 없습니다.");
            }
            if (option.getStockQuantity() < quantity.quantity()) {
                throw new ConflictException("재고가 부족한 상품 옵션이 포함되어 있습니다.");
            }
            option.decreaseStock(quantity.quantity());
        }
    }

    /**
     * 동일 상품의 주문수량을 오버플로 없이 합산한다.
     *
     * @param first 기존 누적 수량
     * @param second 추가할 수량
     * @return 두 수량의 합
     */
    private int safeAddQuantity(int first, int second) {
        try {
            return Math.addExact(first, second);
        } catch (ArithmeticException e) {
            throw new ConflictException("주문수량이 허용 범위를 초과했습니다.");
        }
    }

    /**
     * 같은 옵션에 속한 주문수량을 합치고 상품 ID 일치 여부를 검증한다.
     *
     * @param first 기존 옵션 수량
     * @param second 추가할 옵션 수량
     * @return 합산된 옵션 수량
     */
    private OptionQuantity mergeOptionQuantity(OptionQuantity first, OptionQuantity second) {
        if (!first.productId().equals(second.productId())) {
            throw new ConflictException("상품 옵션 정보가 일치하지 않습니다.");
        }
        return new OptionQuantity(first.productId(), safeAddQuantity(first.quantity(), second.quantity()));
    }

    /**
     * 저장소에 존재하지 않는 고유 결제번호를 제한된 횟수만큼 생성한다.
     *
     * @return 중복되지 않은 결제번호
     * @throws IllegalStateException 제한 횟수 안에 고유 번호를 만들지 못한 경우
     */
    private String generatePaymentNumber() {
        for (int attempt = 0; attempt < IDENTIFIER_GENERATION_ATTEMPTS; attempt++) {
            String paymentNumber = identifierGenerator.generatePaymentNumber();
            if (!paymentRepository.existsByPaymentNumber(paymentNumber)) {
                return paymentNumber;
            }
        }
        throw new IllegalStateException("결제번호를 생성할 수 없습니다.");
    }

    /**
     * 저장소에 존재하지 않는 고유 PG 거래키를 제한된 횟수만큼 생성한다.
     *
     * @return 중복되지 않은 PG 거래키
     * @throws IllegalStateException 제한 횟수 안에 고유 거래키를 만들지 못한 경우
     */
    private String generateTransactionKey() {
        for (int attempt = 0; attempt < IDENTIFIER_GENERATION_ATTEMPTS; attempt++) {
            String transactionKey = identifierGenerator.generateTransactionKey();
            if (!paymentRepository.existsByPgTransactionKey(transactionKey)) {
                return transactionKey;
            }
        }
        throw new IllegalStateException("PG 거래키를 생성할 수 없습니다.");
    }

    /**
     * 주문과 결제 엔티티의 현재 상태를 결제 승인 응답으로 변환한다.
     *
     * @param order 승인된 주문
     * @param payment 승인된 결제
     * @return API 결제 승인 응답
     */
    private PaymentConfirmResponse toResponse(Order order, Payment payment) {
        return PaymentConfirmResponseMapper.from(order, payment);
    }

    /**
     * 조회한 엔티티 목록을 ID 기반 탐색 맵으로 변환한다.
     *
     * @param values 변환할 값 목록
     * @param idExtractor 각 값에서 ID를 추출하는 함수
     * @param <T> 변환할 값 타입
     * @return ID를 키로 사용하는 탐색 맵
     */
    private <T> Map<Long, T> toIdMap(List<T> values, Function<T, Long> idExtractor) {
        return values.stream().collect(Collectors.toMap(
                idExtractor,
                Function.identity(),
                (first, duplicate) -> first,
                HashMap::new));
    }

    private record OptionQuantity(Long productId, int quantity) {
    }
}
