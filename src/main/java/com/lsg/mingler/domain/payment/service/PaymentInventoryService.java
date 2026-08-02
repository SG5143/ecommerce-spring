package com.lsg.mingler.domain.payment.service;

import com.lsg.mingler.domain.order.entity.OrderItem;
import com.lsg.mingler.domain.product.dao.ProductOptionRepository;
import com.lsg.mingler.domain.product.entity.ProductOption;
import com.lsg.mingler.global.error.ConflictException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 주문 상품의 옵션별 수량을 합산하고 일정한 순서로 잠근 뒤 결제 예약 재고를 차감하거나 복구한다.
 */
@Component
@RequiredArgsConstructor
class PaymentInventoryService {

    private final ProductOptionRepository productOptionRepository;

    /**
     * 주문 상품에 필요한 옵션 재고를 잠그고 한 번 차감한다.
     *
     * @param orderItems 재고를 예약할 주문 상품 목록
     */
    void reserve(List<OrderItem> orderItems) {
        Map<Long, OptionQuantity> quantities = aggregate(orderItems);
        Map<Long, ProductOption> options = lockedOptions(quantities);
        for (Map.Entry<Long, OptionQuantity> entry : quantities.entrySet()) {
            ProductOption option = requireMatching(options, entry);
            if (option.getStockQuantity() < entry.getValue().quantity()) {
                throw new ConflictException("재고가 부족한 상품 옵션이 포함되어 있습니다.");
            }
            option.decreaseStock(entry.getValue().quantity());
        }
    }

    /**
     * 확정 실패 또는 취소된 결제에서 예약했던 옵션 재고를 복구한다.
     *
     * @param orderItems 재고를 복구할 주문 상품 목록
     */
    void restore(List<OrderItem> orderItems) {
        Map<Long, OptionQuantity> quantities = aggregate(orderItems);
        Map<Long, ProductOption> options = lockedOptions(quantities);
        for (Map.Entry<Long, OptionQuantity> entry : quantities.entrySet()) {
            requireMatching(options, entry).increaseStock(entry.getValue().quantity());
        }
    }

    private Map<Long, OptionQuantity> aggregate(List<OrderItem> orderItems) {
        TreeMap<Long, OptionQuantity> quantities = new TreeMap<>();
        for (OrderItem item : orderItems) {
            if (item.getProductOptionId() == null) {
                throw new ConflictException("주문 상품의 옵션 정보가 없습니다.");
            }
            quantities.merge(
                    item.getProductOptionId(),
                    new OptionQuantity(item.getProductId(), item.getQuantity()),
                    this::merge);
        }
        return quantities;
    }

    private Map<Long, ProductOption> lockedOptions(Map<Long, OptionQuantity> quantities) {
        return productOptionRepository.findAllByIdInForUpdate(quantities.keySet()).stream()
                .collect(Collectors.toMap(
                        ProductOption::getId,
                        Function.identity(),
                        (first, duplicate) -> first,
                        HashMap::new));
    }

    private ProductOption requireMatching(Map<Long, ProductOption> options, Map.Entry<Long, OptionQuantity> entry) {
        ProductOption option = options.get(entry.getKey());
        if (option == null || !entry.getValue().productId().equals(option.getProductId())) {
            throw new ConflictException("상품 옵션 재고 정보를 찾을 수 없습니다.");
        }
        return option;
    }

    private OptionQuantity merge(OptionQuantity first, OptionQuantity second) {
        if (!first.productId().equals(second.productId())) {
            throw new ConflictException("상품 옵션 정보가 일치하지 않습니다.");
        }
        try {
            return new OptionQuantity(first.productId(), Math.addExact(first.quantity(), second.quantity()));
        } catch (ArithmeticException e) {
            throw new ConflictException("주문수량이 허용 범위를 초과했습니다.");
        }
    }

    private record OptionQuantity(Long productId, int quantity) {
    }

}
