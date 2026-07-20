package com.lsg.mingler.domain.cart.dto;

import java.util.List;

public record CartResponse(List<Item> items, int totalQuantity, long merchandiseTotal) {

    public static CartResponse empty() {
        return new CartResponse(List.of(), 0, 0);
    }

    public record Item(Long id, Long productId, Long optionId, String productName,
                       String optionName, String thumbnailUrl, int quantity,
                       int stockQuantity, int unitPriceAtAdded, int currentUnitPrice,
                       boolean priceChanged, boolean available, String unavailableReason,
                       long lineTotal) {
    }
}
