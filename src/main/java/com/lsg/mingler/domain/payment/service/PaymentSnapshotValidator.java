package com.lsg.mingler.domain.payment.service;

import com.lsg.mingler.domain.order.entity.Order;
import com.lsg.mingler.domain.order.entity.OrderItem;
import com.lsg.mingler.global.error.ConflictException;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 결제 전에 주문과 주문 상품에 저장된 가격·수량·합계 스냅샷의 일관성을 검증
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class PaymentSnapshotValidator {

    /**
     * 주문 상품별 금액과 주문 전체 합계를 다시 계산해 저장된 스냅샷과 비교
     *
     * @param order 검증할 주문
     * @param orderItems 검증할 주문 상품 목록
     */
    static void validate(Order order, List<OrderItem> orderItems) {
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
            int totalAmount = Math.addExact(
                    Math.subtractExact(order.getMerchandiseAmount(), order.getDiscountAmount()),
                    order.getShippingFee());
            if (merchandiseAmount != order.getMerchandiseAmount()
                    || totalAmount != order.getTotalAmount()) {
                throw new ConflictException("주문금액 스냅샷이 일치하지 않습니다.");
            }
        } catch (ArithmeticException e) {
            throw new ConflictException("주문금액이 허용 범위를 초과했습니다.");
        }
    }
}
