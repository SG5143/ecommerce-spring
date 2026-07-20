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

/**
 * 회원 및 비회원 장바구니의 조회, 상품 변경, 로그인 병합, 만료 정리를 담당한다.
 * 장바구니 변경 작업은 소유 장바구니를 잠근 뒤 처리하며, 응답에는 현재 상품 상태와 가격을 반영한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CartService {

    /** 장바구니 상품 한 행에 허용하는 최대 수량 */
    public static final int MAX_ITEM_QUANTITY = 99;
    /** 한 번의 담기 요청에서 허용하는 최대 상품 옵션 행 수 */
    private static final int MAX_ADD_LINES = 20;
    /** 하나의 장바구니에 허용하는 최대 상품 행 수 */
    private static final int MAX_CART_LINES = 100;

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final ProductOptionRepository productOptionRepository;
    private final CartExpirationService cartExpirationService;

    /**
     * 식별 정보에 해당하는 장바구니를 현재 상품 정보와 함께 조회한다.
     * 장바구니가 없거나 만료된 경우에는 빈 응답을 반환한다.
     *
     * @param memberId 로그인 회원 ID. 비회원이면 {@code null}
     * @param guestTokenHash 비회원 장바구니 토큰 해시. 회원이면 {@code null}
     * @return 현재 장바구니 응답
     */
    @Transactional(readOnly = true)
    public CartResponse getCart(Long memberId, String guestTokenHash) {
        Optional<Cart> cart = findCart(memberId, guestTokenHash, false);
        return cart.map(this::toResponse).orElseGet(CartResponse::empty);
    }

    /**
     * 식별 정보에 해당하는 장바구니의 전체 상품 수량만 조회한다.
     * 장바구니가 없거나 만료된 경우에는 0을 반환한다.
     *
     * @param memberId 로그인 회원 ID. 비회원이면 {@code null}
     * @param guestTokenHash 비회원 장바구니 토큰 해시. 회원이면 {@code null}
     * @return 모든 장바구니 항목의 수량 합계
     */
    @Transactional(readOnly = true)
    public int getCartCount(Long memberId, String guestTokenHash) {
        Optional<Cart> cart = findCart(memberId, guestTokenHash, false);
        return cart.map(value -> Math.toIntExact(cartItemRepository.sumQuantityByCartId(value.getId())))
                .orElse(0);
    }

    /**
     * 요청 상품을 검증한 뒤 기존 동일 상품·옵션에는 수량을 합산하고, 없으면 새 행으로 추가한다.
     *
     * @param memberId 로그인 회원 ID. 비회원이면 {@code null}
     * @param guestTokenHash 비회원 장바구니 토큰 해시. 회원이면 {@code null}
     * @param request 추가할 상품과 옵션 및 수량
     * @return 변경 후 장바구니 응답
     */
    @Transactional
    public CartResponse addItems(Long memberId, String guestTokenHash, CartItemsAddRequest request) {
        // 동일 상품·옵션 요청을 먼저 합쳐 검증과 저장을 한 번씩만 수행한다.
        Map<VariantKey, Integer> requestedItems = validateAndNormalize(request);
        List<ValidatedVariant> variants = requestedItems.entrySet().stream()
                .map(entry -> validateVariant(entry.getKey(), entry.getValue()))
                .toList();

        Cart cart = getOrCreateCart(memberId, guestTokenHash);
        List<CartItem> currentItems = cartItemRepository.findAllByCartIdOrderByCreatedAtAscIdAsc(cart.getId());
        // 기존 행과 겹치지 않는 요청만 신규 행 수로 계산한다.
        long newLineCount = variants.stream()
                .filter(variant -> currentItems.stream().noneMatch(item -> sameVariant(item, variant.key())))
                .count();
        if (currentItems.size() + newLineCount > MAX_CART_LINES) {
            throw new IllegalArgumentException("장바구니에는 최대 100개 상품만 담을 수 있습니다.");
        }

        for (ValidatedVariant variant : variants) {
            // DB 유니크 키와 같은 상품·옵션 조합을 기준으로 신규 생성 또는 수량 합산한다.
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

    /**
     * 장바구니 소유 항목의 수량을 현재 판매 상태와 재고 범위 안에서 변경한다.
     *
     * @param memberId 로그인 회원 ID. 비회원이면 {@code null}
     * @param guestTokenHash 비회원 장바구니 토큰 해시. 회원이면 {@code null}
     * @param itemId 변경할 장바구니 상품 ID
     * @param quantity 변경할 수량
     * @return 변경 후 장바구니 응답
     */
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

    /**
     * 요청한 항목이 모두 현재 장바구니 소유인지 일괄 확인한 뒤 한 번에 삭제한다.
     *
     * @param memberId 로그인 회원 ID. 비회원이면 {@code null}
     * @param guestTokenHash 비회원 장바구니 토큰 해시. 회원이면 {@code null}
     * @param itemIds 삭제할 장바구니 상품 ID 목록
     * @return 변경 후 장바구니 응답
     */
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
        // 개별 조회 N회를 피하면서 다른 장바구니 항목이 섞였는지도 함께 확인한다.
        List<CartItem> ownedItems = cartItemRepository.findAllByCartIdAndIdIn(cart.getId(), distinctIds);
        if (ownedItems.size() != distinctIds.size()) {
            throw notFound("장바구니 상품을 찾을 수 없습니다.");
        }
        cartItemRepository.deleteAllByCartIdAndIdIn(cart.getId(), distinctIds);
        extendGuestExpiration(cart);
        return toResponse(cart);
    }

    /**
     * 로그인한 회원의 장바구니에 비회원 장바구니 상품을 합치고 비회원 장바구니를 제거한다.
     * 합산 수량이 재고 또는 최대 허용 수량을 넘으면 가능한 최대치로 조정한다.
     *
     * @param memberId 로그인 회원 ID
     * @param guestTokenHash 병합할 비회원 장바구니 토큰 해시
     * @return 병합 건수, 수량 조정 항목 ID, 병합 후 장바구니 응답
     */
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
            // 회원 장바구니에 같은 상품·옵션이 있으면 하나의 행으로 수량을 합친다.
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

        // 신규 저장 항목의 ID를 응답에 담기 전에 INSERT를 DB에 반영한다.
        cartItemRepository.flush();
        cartRepository.delete(guestCart);
        List<Long> adjustedIds = adjustedItems.stream().map(CartItem::getId).toList();
        return new CartMergeResponse(guestItems.size(), adjustedIds, toResponse(memberCart));
    }

    /** 매일 오전 3시에 보관 기간이 지난 모든 비회원 장바구니를 일괄 삭제한다. */
    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")
    @Transactional
    public void deleteExpiredGuestCarts() {
        int deletedCount = cartRepository.deleteExpiredGuestCarts(LocalDateTime.now());
        if (deletedCount > 0) {
            log.info("만료된 비회원 장바구니 {}건을 삭제했습니다.", deletedCount);
        }
    }

    /**
     * 담기 요청의 필수값과 요청 행 수를 검증하고 동일 상품·옵션의 수량을 합산한다.
     * 입력 순서를 유지해 이후 저장과 응답 처리의 예측 가능성을 보장한다.
     */
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

    /** 수량이 한 행의 허용 범위인 1~99인지 검증한다. */
    private void validateQuantity(Integer quantity) {
        if (quantity == null || quantity < 1 || quantity > MAX_ITEM_QUANTITY) {
            throw new IllegalArgumentException("수량은 1개 이상 99개 이하로 입력해주세요.");
        }
    }

    /**
     * 상품·옵션 관계와 판매 상태를 검증하고 재고 및 현재 단가를 계산한다.
     * 옵션 상품은 활성 옵션 재고를, 단일 구성 상품은 상품 재고를 사용한다.
     */
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

        // 옵션 존재 여부를 기준으로 옵션 필수/금지 규칙을 대칭적으로 적용한다.
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

    /** 요청 수량이 품절 상태 또는 현재 재고를 초과하는지 검증한다. */
    private void validateQuantityAgainstStock(int quantity, int stockQuantity) {
        if (stockQuantity <= 0) {
            throw conflict("품절된 상품입니다.");
        }
        if (quantity > stockQuantity) {
            throw conflict("재고가 부족합니다. 최대 " + stockQuantity + "개까지 담을 수 있습니다.");
        }
    }

    /**
     * 소유 장바구니를 잠금 조회하고, 없으면 동시 생성에 안전한 INSERT IGNORE 방식으로 생성한다.
     * 회원 ID가 있으면 회원 장바구니를 우선하며, 그렇지 않으면 비회원 토큰을 사용한다.
     */
    private Cart getOrCreateCart(Long memberId, String guestTokenHash) {
        Optional<Cart> existing = findCart(memberId, guestTokenHash, true);
        if (existing.isPresent()) {
            return existing.get();
        }
        if (memberId == null && guestTokenHash == null) {
            throw new IllegalArgumentException("비회원 장바구니 식별 정보가 필요합니다.");
        }
        if (memberId != null) {
            // 동시 요청이 먼저 생성해도 유니크 키 충돌 없이 기존 장바구니를 다시 조회한다.
            cartRepository.createMemberCartIfAbsent(memberId);
            return cartRepository.findByMemberIdForUpdate(memberId)
                    .orElseThrow(() -> new IllegalStateException("회원 장바구니를 생성하지 못했습니다."));
        }
        // 비회원 장바구니는 생성 시점부터 토큰 유효기간과 같은 만료 시각을 가진다.
        LocalDateTime expiresAt = nextGuestExpiration();
        cartRepository.createGuestCartIfAbsent(guestTokenHash, expiresAt);
        return cartRepository.findByGuestTokenHashForUpdate(guestTokenHash)
                .orElseThrow(() -> new IllegalStateException("비회원 장바구니를 생성하지 못했습니다."));
    }

    /** 변경 작업을 위해 장바구니를 잠금 조회하고, 없거나 만료됐으면 404 예외를 발생시킨다. */
    private Cart requireCart(Long memberId, String guestTokenHash) {
        return findCart(memberId, guestTokenHash, true)
                .orElseThrow(() -> notFound("장바구니를 찾을 수 없습니다."));
    }

    /**
     * 회원 ID 또는 비회원 토큰으로 장바구니를 조회한다.
     * 변경 경로에서는 비관적 쓰기 잠금을 사용하고, 읽기 경로에서는 일반 조회를 사용한다.
     */
    private Optional<Cart> findCart(Long memberId, String guestTokenHash, boolean lock) {
        Optional<Cart> found;
        // 인증된 회원 ID가 있으면 비회원 토큰보다 우선해 회원 장바구니를 선택한다.
        if (memberId != null) {
            found = lock ? cartRepository.findByMemberIdForUpdate(memberId)
                    : cartRepository.findByMemberId(memberId);
        } else if (guestTokenHash != null) {
            found = lock ? cartRepository.findByGuestTokenHashForUpdate(guestTokenHash)
                    : cartRepository.findByGuestTokenHash(guestTokenHash);
        } else {
            return Optional.empty();
        }
        LocalDateTime now = LocalDateTime.now();
        if (found.isPresent() && found.get().isExpired(now)) {
            if (lock) {
                // 비관적 잠금을 가진 트랜잭션을 중단하면 새 삭제 트랜잭션이 같은 행을 기다릴 수 있다.
                cartRepository.delete(found.get());
            } else {
                // readOnly 트랜잭션 밖의 REQUIRES_NEW 트랜잭션에서 조건부 삭제한다.
                cartExpirationService.deleteExpiredGuestCart(found.get().getId(), now);
            }
            return Optional.empty();
        }
        return found;
    }

    /** 비회원 장바구니가 변경될 때 마지막 활동 시점부터 유효기간을 다시 연장한다. */
    private void extendGuestExpiration(Cart cart) {
        cart.extendExpiration(nextGuestExpiration());
    }

    /** 현재 시각을 기준으로 다음 비회원 장바구니 만료 시각을 계산한다. */
    private LocalDateTime nextGuestExpiration() {
        return LocalDateTime.now().plus(GuestCartTokenManager.VALIDITY);
    }

    /**
     * 병합할 상품의 현재 재고와 행 최대 수량 중 더 작은 값을 반환한다.
     * 판매 중지 등으로 현재 검증이 불가능한 상품은 기존 수량 보존을 위해 행 최대 수량을 사용한다.
     */
    private int resolveMergeMaximum(CartItem item) {
        try {
            ValidatedVariant variant = validateVariant(
                    new VariantKey(item.getProductId(), item.getProductOptionId()), 1);
            return Math.min(variant.stockQuantity(), MAX_ITEM_QUANTITY);
        } catch (ResponseStatusException | IllegalArgumentException e) {
            return MAX_ITEM_QUANTITY;
        }
    }

    /**
     * 장바구니 항목과 현재 상품·옵션 정보를 일괄 조회해 가격 및 구매 가능 상태를 포함한 응답으로 변환한다.
     * 상품과 옵션을 ID Map으로 구성해 항목 수에 비례하는 추가 조회를 방지한다.
     */
    private CartResponse toResponse(Cart cart) {
        List<CartItem> cartItems = cartItemRepository.findAllByCartIdOrderByCreatedAtAscIdAsc(cart.getId());
        if (cartItems.isEmpty()) {
            return CartResponse.empty();
        }

        // 응답 변환 중 항목별 조회가 발생하지 않도록 연관 상품과 옵션을 한 번에 조회한다.
        Set<Long> productIds = new LinkedHashSet<>();
        Set<Long> optionIds = new LinkedHashSet<>();
        for (CartItem item : cartItems) {
            productIds.add(item.getProductId());
            if (item.getProductOptionId() != null) {
                optionIds.add(item.getProductOptionId());
            }
        }

        Map<Long, Product> productsById = new LinkedHashMap<>();
        productRepository.findAllById(productIds)
                .forEach(product -> productsById.put(product.getId(), product));
        Map<Long, ProductOption> optionsById = new LinkedHashMap<>();
        if (!optionIds.isEmpty()) {
            productOptionRepository.findAllById(optionIds)
                    .forEach(option -> optionsById.put(option.getId(), option));
        }
        Set<Long> productIdsWithOptions = new LinkedHashSet<>(
                productOptionRepository.findProductIdsWithOptions(productIds));

        List<CartResponse.Item> items = cartItems.stream()
                .map(item -> toResponseItem(item,
                        productsById.get(item.getProductId()),
                        optionsById.get(item.getProductOptionId()),
                        productIdsWithOptions.contains(item.getProductId())))
                .toList();
        int totalQuantity = items.stream().mapToInt(CartResponse.Item::quantity).sum();
        long merchandiseTotal = items.stream()
                .filter(CartResponse.Item::available)
                .mapToLong(CartResponse.Item::lineTotal)
                .sum();
        return new CartResponse(items, totalQuantity, merchandiseTotal);
    }

    /**
     * 저장 당시 정보와 현재 상품 정보를 조합해 단일 장바구니 응답 항목을 만든다.
     * 삭제·판매 중지·옵션 변경·재고 부족 상태도 항목을 제거하지 않고 구매 불가 사유로 표현한다.
     */
    private CartResponse.Item toResponseItem(CartItem item, Product product, ProductOption option, boolean productHasOptions) {
        String productName = product == null ? "삭제된 상품" : product.getName();
        String optionName = option == null ? null : option.getName();
        String thumbnailUrl = product == null ? null : product.getThumbnailUrl();
        int currentUnitPrice = item.getUnitPriceAtAdded();
        int stockQuantity = 0;
        String unavailableReason = null;

        // 사용자에게 가장 근본적인 구매 불가 원인부터 우선순위대로 판정한다.
        if (product == null || Product.STATUS_HIDDEN.equals(product.getStatus())) {
            unavailableReason = "판매 중지된 상품입니다.";
        } else if (!product.isOnSale()) {
            unavailableReason = "현재 판매 중인 상품이 아닙니다.";
        } else if (item.getProductOptionId() != null
                && (option == null || !product.getId().equals(option.getProductId())
                || !Boolean.TRUE.equals(option.getIsActive()))) {
            unavailableReason = "현재 선택할 수 없는 옵션입니다.";
        } else if (item.getProductOptionId() == null && productHasOptions) {
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

    /** 장바구니 항목과 요청 키가 동일한 상품·옵션 조합인지 확인한다. */
    private boolean sameVariant(CartItem item, VariantKey key) {
        return item.getProductId().equals(key.productId())
                && java.util.Objects.equals(item.getProductOptionId(), key.optionId());
    }

    /** 서비스 검증 실패를 HTTP 404 예외로 변환한다. */
    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    /** 현재 상품 상태와 요청이 충돌하는 상황을 HTTP 409 예외로 변환한다. */
    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    /** 상품과 선택 옵션 조합을 중복 제거 및 조회 키로 사용하는 값 객체 */
    private record VariantKey(Long productId, Long optionId) {
    }

    /** 검증을 통과한 상품·옵션과 요청 수량, 재고, 현재 단가를 함께 전달하는 내부 값 객체 */
    private record ValidatedVariant(VariantKey key, int requestedQuantity, int stockQuantity,
                                    int currentUnitPrice, Product product, ProductOption option) {
    }
}
