package com.lsg.mingler.domain.cart.dto;

import java.util.List;

public record CartMergeResponse(int mergedItemCount, List<Long> adjustedItemIds, CartResponse cart) {
}
