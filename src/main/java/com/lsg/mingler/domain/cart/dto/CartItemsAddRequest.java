package com.lsg.mingler.domain.cart.dto;

import java.util.List;

public record CartItemsAddRequest(List<Item> items) {

    public record Item(Long productId, Long optionId, Integer quantity) {
    }
}
