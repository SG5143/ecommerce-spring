package com.lsg.mingler.domain.cart.service;

import com.lsg.mingler.domain.cart.dao.CartItemRepository;
import com.lsg.mingler.domain.cart.dao.CartRepository;
import com.lsg.mingler.domain.cart.dto.CartItemsAddRequest;
import com.lsg.mingler.domain.cart.dto.CartMergeResponse;
import com.lsg.mingler.domain.cart.dto.CartResponse;
import com.lsg.mingler.domain.cart.entity.Cart;
import com.lsg.mingler.domain.cart.entity.CartItem;
import com.lsg.mingler.domain.product.dao.ProductOptionRepository;
import com.lsg.mingler.domain.product.dao.ProductRepository;
import com.lsg.mingler.domain.product.entity.Product;
import com.lsg.mingler.domain.product.entity.ProductOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartService {

    public static final int MAX_ITEM_QUANTITY = 99;
    private static final int MAX_ADD_LINES = 20;
    private static final int MAX_CART_LINES = 100;

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final ProductOptionRepository productOptionRepository;

    @Transactional
    public CartResponse getCart(Long memberId, String guestTokenHash) {
        Optional<Cart> cart = findCart(memberId, guestTokenHash, false);
        return cart.map(this::toResponse).orElseGet(CartResponse::empty);
    }

    @Transactional
    public CartResponse addItems(Long memberId, String guestTokenHash, CartItemsAddRequest request) {
        Map<VariantKey, Integer> requestedItems = validateAndNormalize(request);
        List<ValidatedVariant> variants = requestedItems.entrySet().stream()
                .map(entry -> validateVariant(entry.getKey(), entry.getValue()))
                .toList();

        Cart cart = getOrCreateCart(memberId, guestTokenHash);
        List<CartItem> currentItems = cartItemRepository.findAllByCartIdOrderByCreatedAtAscIdAsc(cart.getId());
        long newLineCount = variants.stream()
                .filter(variant -> currentItems.stream().noneMatch(item -> sameVariant(item, variant.key())))
                .count();
        if (currentItems.size() + newLineCount > MAX_CART_LINES) {
            throw new IllegalArgumentException("장바구니에는 최대 100개 상품만 담을 수 있습니다.");
        }

        for (ValidatedVariant variant : variants) {
            CartItem item = cartItemRepository.findVariant(
                            cart.getId(), variant.key().productId(), variant.key().optionId())
                    .orElse(null);
            int nextQuantity = variant.requestedQuantity() + (item == null ? 0 : item.getQuantity());
            validateQuantityAgainstStock(nextQuantity, variant.stockQuantity());
            if (item == null) {
                item = CartItem.builder()
                        .cartId(cart.getId())
                        .productId(variant.key().productId())
                        .productOptionId(variant.key().optionId())
                        .quantity(nextQuantity)
                        .unitPriceAtAdded(variant.currentUnitPrice())
                        .build();
            } else {
                item.changeQuantity(nextQuantity);
            }
            cartItemRepository.save(item);
        }
        extendGuestExpiration(cart);
        return toResponse(cart);
    }

    @Transactional
    public CartResponse updateQuantity(Long memberId, String guestTokenHash, Long itemId, Integer quantity) {
        validateQuantity(quantity);
        Cart cart = requireCart(memberId, guestTokenHash);
        CartItem item = cartItemRepository.findByCartIdAndId(cart.getId(), itemId)
                .orElseThrow(() -> notFound("장바구니 상품을 찾을 수 없습니다."));
        ValidatedVariant variant = validateVariant(
                new VariantKey(item.getProductId(), item.getProductOptionId()), quantity);
        validateQuantityAgainstStock(quantity, variant.stockQuantity());
        item.changeQuantity(quantity);
        extendGuestExpiration(cart);
        return toResponse(cart);
    }

    @Transactional
    public CartResponse deleteItems(Long memberId, String guestTokenHash, Collection<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            throw new IllegalArgumentException("삭제할 상품을 선택해주세요.");
        }
        Set<Long> distinctIds = new LinkedHashSet<>(itemIds);
        if (distinctIds.size() > MAX_CART_LINES || distinctIds.contains(null)) {
            throw new IllegalArgumentException("삭제할 상품 목록이 올바르지 않습니다.");
        }

        Cart cart = requireCart(memberId, guestTokenHash);
        boolean ownsAllItems = distinctIds.stream()
                .allMatch(id -> cartItemRepository.findByCartIdAndId(cart.getId(), id).isPresent());
        if (!ownsAllItems) {
            throw notFound("장바구니 상품을 찾을 수 없습니다.");
        }
        cartItemRepository.deleteAllByCartIdAndIdIn(cart.getId(), distinctIds);
        extendGuestExpiration(cart);
        return toResponse(cart);
    }

    @Transactional
    public CartMergeResponse mergeGuestCart(Long memberId, String guestTokenHash) {
        if (memberId == null) {
            throw new IllegalArgumentException("회원 정보가 필요합니다.");
        }
        if (guestTokenHash == null) {
            return new CartMergeResponse(0, List.of(), getCart(memberId, null));
        }

        Optional<Cart> guestOptional = findCart(null, guestTokenHash, true);
        if (guestOptional.isEmpty()) {
            return new CartMergeResponse(0, List.of(), getCart(memberId, null));
        }

        Cart guestCart = guestOptional.get();
        Cart memberCart = getOrCreateCart(memberId, null);
        List<CartItem> guestItems = cartItemRepository.findAllByCartIdOrderByCreatedAtAscIdAsc(guestCart.getId());
        List<CartItem> adjustedItems = new ArrayList<>();

        for (CartItem guestItem : guestItems) {
            CartItem memberItem = cartItemRepository.findVariant(
                            memberCart.getId(), guestItem.getProductId(), guestItem.getProductOptionId())
                    .orElse(null);
            int combinedQuantity = guestItem.getQuantity() + (memberItem == null ? 0 : memberItem.getQuantity());
            int maximum = resolveMergeMaximum(guestItem);
            int mergedQuantity = Math.min(combinedQuantity, maximum);

            if (memberItem == null) {
                memberItem = CartItem.builder()
                        .cartId(memberCart.getId())
                        .productId(guestItem.getProductId())
                        .productOptionId(guestItem.getProductOptionId())
                        .quantity(mergedQuantity)
                        .unitPriceAtAdded(guestItem.getUnitPriceAtAdded())
                        .build();
            } else {
                memberItem.changeQuantity(mergedQuantity);
            }
            cartItemRepository.save(memberItem);
            if (mergedQuantity != combinedQuantity) {
                adjustedItems.add(memberItem);
            }
        }

        cartItemRepository.flush();
        cartRepository.delete(guestCart);
        List<Long> adjustedIds = adjustedItems.stream().map(CartItem::getId).toList();
        return new CartMergeResponse(guestItems.size(), adjustedIds, toResponse(memberCart));
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")
    @Transactional
    public void deleteExpiredGuestCarts() {
        int deletedCount = cartRepository.deleteExpiredGuestCarts(LocalDateTime.now());
        if (deletedCount > 0) {
            log.info("만료된 비회원 장바구니 {}건을 삭제했습니다.", deletedCount);
        }
    }

    private Map<VariantKey, Integer> validateAndNormalize(CartItemsAddRequest request) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("장바구니에 담을 상품을 선택해주세요.");
        }
        if (request.items().size() > MAX_ADD_LINES) {
            throw new IllegalArgumentException("한 번에 최대 20개 옵션을 담을 수 있습니다.");
        }

        Map<VariantKey, Integer> normalized = new LinkedHashMap<>();
        for (CartItemsAddRequest.Item item : request.items()) {
            if (item == null || item.productId() == null) {
                throw new IllegalArgumentException("상품 정보가 올바르지 않습니다.");
            }
            validateQuantity(item.quantity());
            VariantKey key = new VariantKey(item.productId(), item.optionId());
            int quantity = normalized.getOrDefault(key, 0) + item.quantity();
            validateQuantity(quantity);
            normalized.put(key, quantity);
        }
        return normalized;
    }

    private void validateQuantity(Integer quantity) {
        if (quantity == null || quantity < 1 || quantity > MAX_ITEM_QUANTITY) {
            throw new IllegalArgumentException("수량은 1개 이상 99개 이하로 입력해주세요.");
        }
    }

    private ValidatedVariant validateVariant(VariantKey key, int requestedQuantity) {
        Product product = productRepository.findById(key.productId())
                .orElseThrow(() -> notFound("상품을 찾을 수 없습니다."));
        if (!product.isOnSale()) {
            throw conflict("현재 판매 중인 상품이 아닙니다.");
        }

        boolean hasOptions = productOptionRepository.existsByProductId(product.getId());
        ProductOption option = null;
        int stockQuantity;
        int currentUnitPrice = product.getDisplayPrice();

        if (hasOptions) {
            if (key.optionId() == null) {
                throw new IllegalArgumentException("상품 옵션을 선택해주세요.");
            }
            option = productOptionRepository.findById(key.optionId())
                    .orElseThrow(() -> notFound("상품 옵션을 찾을 수 없습니다."));
            if (!product.getId().equals(option.getProductId())) {
                throw new IllegalArgumentException("상품에 속하지 않은 옵션입니다.");
            }
            if (!Boolean.TRUE.equals(option.getIsActive())) {
                throw conflict("현재 선택할 수 없는 상품 옵션입니다.");
            }
            stockQuantity = option.getStockQuantity();
            currentUnitPrice += option.getExtraPrice();
        } else {
            if (key.optionId() != null) {
                throw new IllegalArgumentException("옵션이 없는 상품입니다.");
            }
            stockQuantity = product.getStockQuantity();
        }

        validateQuantityAgainstStock(requestedQuantity, stockQuantity);
        return new ValidatedVariant(key, requestedQuantity, stockQuantity, currentUnitPrice, product, option);
    }

    private void validateQuantityAgainstStock(int quantity, int stockQuantity) {
        if (stockQuantity <= 0) {
            throw conflict("품절된 상품입니다.");
        }
        if (quantity > stockQuantity) {
            throw conflict("재고가 부족합니다. 최대 " + stockQuantity + "개까지 담을 수 있습니다.");
        }
    }

    private Cart getOrCreateCart(Long memberId, String guestTokenHash) {
        Optional<Cart> existing = findCart(memberId, guestTokenHash, true);
        if (existing.isPresent()) {
            return existing.get();
        }
        if (memberId == null && guestTokenHash == null) {
            throw new IllegalArgumentException("비회원 장바구니 식별 정보가 필요합니다.");
        }
        if (memberId != null) {
            cartRepository.createMemberCartIfAbsent(memberId);
            return cartRepository.findByMemberIdForUpdate(memberId)
                    .orElseThrow(() -> new IllegalStateException("회원 장바구니를 생성하지 못했습니다."));
        }
        LocalDateTime expiresAt = nextGuestExpiration();
        cartRepository.createGuestCartIfAbsent(guestTokenHash, expiresAt);
        return cartRepository.findByGuestTokenHashForUpdate(guestTokenHash)
                .orElseThrow(() -> new IllegalStateException("비회원 장바구니를 생성하지 못했습니다."));
    }

    private Cart requireCart(Long memberId, String guestTokenHash) {
        return findCart(memberId, guestTokenHash, true)
                .orElseThrow(() -> notFound("장바구니를 찾을 수 없습니다."));
    }

    private Optional<Cart> findCart(Long memberId, String guestTokenHash, boolean lock) {
        Optional<Cart> found;
        if (memberId != null) {
            found = lock ? cartRepository.findByMemberIdForUpdate(memberId)
                    : cartRepository.findByMemberId(memberId);
        } else if (guestTokenHash != null) {
            found = lock ? cartRepository.findByGuestTokenHashForUpdate(guestTokenHash)
                    : cartRepository.findByGuestTokenHash(guestTokenHash);
        } else {
            return Optional.empty();
        }
        if (found.isPresent() && found.get().isExpired(LocalDateTime.now())) {
            cartRepository.delete(found.get());
            return Optional.empty();
        }
        return found;
    }

    private void extendGuestExpiration(Cart cart) {
        cart.extendExpiration(nextGuestExpiration());
    }

    private LocalDateTime nextGuestExpiration() {
        return LocalDateTime.now().plus(GuestCartTokenManager.VALIDITY);
    }

    private int resolveMergeMaximum(CartItem item) {
        try {
            ValidatedVariant variant = validateVariant(
                    new VariantKey(item.getProductId(), item.getProductOptionId()), 1);
            return Math.min(variant.stockQuantity(), MAX_ITEM_QUANTITY);
        } catch (ResponseStatusException | IllegalArgumentException e) {
            return MAX_ITEM_QUANTITY;
        }
    }

    private CartResponse toResponse(Cart cart) {
        List<CartResponse.Item> items = cartItemRepository.findAllByCartIdOrderByCreatedAtAscIdAsc(cart.getId()).stream()
                .map(this::toResponseItem)
                .toList();
        int totalQuantity = items.stream().mapToInt(CartResponse.Item::quantity).sum();
        long merchandiseTotal = items.stream()
                .filter(CartResponse.Item::available)
                .mapToLong(CartResponse.Item::lineTotal)
                .sum();
        return new CartResponse(items, totalQuantity, merchandiseTotal);
    }

    private CartResponse.Item toResponseItem(CartItem item) {
        Product product = productRepository.findById(item.getProductId()).orElse(null);
        ProductOption option = item.getProductOptionId() == null ? null
                : productOptionRepository.findById(item.getProductOptionId()).orElse(null);

        String productName = product == null ? "삭제된 상품" : product.getName();
        String optionName = option == null ? null : option.getName();
        String thumbnailUrl = product == null ? null : product.getThumbnailUrl();
        int currentUnitPrice = item.getUnitPriceAtAdded();
        int stockQuantity = 0;
        String unavailableReason = null;

        if (product == null || Product.STATUS_HIDDEN.equals(product.getStatus())) {
            unavailableReason = "판매 중지된 상품입니다.";
        } else if (!product.isOnSale()) {
            unavailableReason = "현재 판매 중인 상품이 아닙니다.";
        } else if (item.getProductOptionId() != null
                && (option == null || !product.getId().equals(option.getProductId())
                || !Boolean.TRUE.equals(option.getIsActive()))) {
            unavailableReason = "현재 선택할 수 없는 옵션입니다.";
        } else if (item.getProductOptionId() == null && productOptionRepository.existsByProductId(product.getId())) {
            unavailableReason = "상품 옵션을 다시 선택해주세요.";
        } else {
            currentUnitPrice = product.getDisplayPrice() + (option == null ? 0 : option.getExtraPrice());
            stockQuantity = option == null ? product.getStockQuantity() : option.getStockQuantity();
            if (stockQuantity <= 0) {
                unavailableReason = "품절된 상품입니다.";
            } else if (item.getQuantity() > stockQuantity) {
                unavailableReason = "재고가 부족합니다. 수량을 " + stockQuantity + "개 이하로 변경해주세요.";
            }
        }

        boolean available = unavailableReason == null;
        return new CartResponse.Item(item.getId(), item.getProductId(), item.getProductOptionId(),
                productName, optionName, thumbnailUrl, item.getQuantity(), stockQuantity,
                item.getUnitPriceAtAdded(), currentUnitPrice,
                item.getUnitPriceAtAdded() != currentUnitPrice, available, unavailableReason,
                (long) currentUnitPrice * item.getQuantity());
    }

    private boolean sameVariant(CartItem item, VariantKey key) {
        return item.getProductId().equals(key.productId())
                && java.util.Objects.equals(item.getProductOptionId(), key.optionId());
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private record VariantKey(Long productId, Long optionId) {
    }

    private record ValidatedVariant(VariantKey key, int requestedQuantity, int stockQuantity,
                                    int currentUnitPrice, Product product, ProductOption option) {
    }
}
