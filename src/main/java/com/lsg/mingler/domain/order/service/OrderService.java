package com.lsg.mingler.domain.order.service;

import com.lsg.mingler.domain.cart.dao.CartItemRepository;
import com.lsg.mingler.domain.cart.dao.CartRepository;
import com.lsg.mingler.domain.cart.entity.Cart;
import com.lsg.mingler.domain.cart.entity.CartItem;
import com.lsg.mingler.domain.member.dao.MemberRepository;
import com.lsg.mingler.domain.member.entity.Member;
import com.lsg.mingler.domain.order.dao.OrderItemRepository;
import com.lsg.mingler.domain.order.dao.OrderRepository;
import com.lsg.mingler.domain.order.dto.OrderCreateRequest;
import com.lsg.mingler.domain.order.dto.OrderCreateResponse;
import com.lsg.mingler.domain.order.entity.Order;
import com.lsg.mingler.domain.order.entity.OrderItem;
import com.lsg.mingler.domain.product.dao.CategoryRepository;
import com.lsg.mingler.domain.product.dao.ProductOptionRepository;
import com.lsg.mingler.domain.product.dao.ProductRepository;
import com.lsg.mingler.domain.product.entity.Category;
import com.lsg.mingler.domain.product.entity.Product;
import com.lsg.mingler.domain.product.entity.ProductOption;
import com.lsg.mingler.global.error.AuthenticationException;
import com.lsg.mingler.global.error.ConflictException;
import com.lsg.mingler.global.error.ResourceNotFoundException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {

    private static final int MAX_ORDER_LINES = 100; // 주문 한도
    private static final int MAX_QUANTITY = 99; // 주문수량 한도
    private static final int IDENTIFIER_GENERATION_ATTEMPTS = 5; // 식별자 재시도 횟수
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"); // 이메일 형식

    private final OrderRepository orderRepository; // 주문 저장소
    private final OrderItemRepository orderItemRepository; // 주문 상품 저장소
    private final CartRepository cartRepository; // 장바구니 저장소
    private final CartItemRepository cartItemRepository; // 장바구니 상품 저장소
    private final MemberRepository memberRepository; // 회원 저장소
    private final ProductRepository productRepository; // 상품 저장소
    private final ProductOptionRepository productOptionRepository; // 상품 옵션 저장소
    private final CategoryRepository categoryRepository; // 카테고리 저장소
    private final OrderIdentifierGenerator identifierGenerator; // 주문 식별자 생성기

    /**
     * 선택한 장바구니 항목을 현재 상품 정보로 재검증하고 결제 전 주문서와 상품 스냅샷을 생성한다.
     * 주문 생성 단계에서는 장바구니를 비우거나 재고를 차감하지 않는다.
     *
     * <p>처리 순서:</p>
     * <ol>
     *   <li>요청 항목이 실제 요청자의 장바구니 소유인지 검증한다.</li>
     *   <li>회원은 DB 회원정보를, 비회원은 요청값을 주문자 스냅샷으로 사용한다.</li>
     *   <li>장바구니에 담은 당시가 아닌 현재 판매 상태·가격·재고로 주문 가능 여부를 재검증한다.</li>
     *   <li>고유 주문번호와 비회원 조회 토큰을 생성하고 주문을 저장한다.</li>
     *   <li>주문 ID 확정 후 상품 스냅샷(OrderItem)을 저장한다. 중간 실패 시 전체 롤백된다.</li>
     * </ol>
     *
     * @param memberId           인증된 회원 ID. 비회원 요청은 {@code null}
     * @param guestCartTokenHash 비회원 장바구니 식별용 SHA-256 토큰 해시. 회원 요청은 {@code null}
     * @param request            주문 생성 요청 (장바구니 항목 ID 목록, 주문자·수령인 정보, 배송 요청사항)
     * @return 생성된 주문서 정보. 비회원인 경우 {@code guestOrderToken} 원문을 포함.
     * @throws IllegalArgumentException 입력값 검증 실패 시 (400)
     * @throws ResourceNotFoundException 요청한 장바구니·상품 정보를 찾을 수 없는 경우
     * @throws ConflictException 재고 부족·판매 중단 등 현재 상태와 주문 요청이 충돌하는 경우
     * @throws com.lsg.mingler.global.error.AuthenticationException 회원 계정 이상 시 (401)
     */
    @Transactional
    public OrderCreateResponse createOrder(Long memberId, String guestCartTokenHash, OrderCreateRequest request) {

        // 1. 요청 항목이 실제 요청자의 장바구니 소유인지 먼저 확정한다.
        List<Long> cartItemIds = validateAndNormalizeItemIds(request);
        Cart cart = requireOwnedCart(memberId, guestCartTokenHash);
        List<CartItem> cartItems = requireOwnedItems(cart.getId(), cartItemIds);

        // 2. 회원은 회원정보를, 비회원은 요청값을 주문자 스냅샷으로 사용한다.
        OrdererSnapshot orderer = resolveOrderer(memberId, request.orderer());
        ReceiverSnapshot receiver = validateReceiver(request.receiver());
        String deliveryMessage = normalizeOptional(request.deliveryMessage(), 255, "배송 요청사항은 255자 이하여야 합니다.");

        // 3. 장바구니에 담았을 당시 값이 아닌 현재 판매 상태·가격·재고로 주문 가능 여부를 재검증한다.
        SnapshotContext context = loadSnapshotContext(cartItems);
        List<ItemSnapshot> snapshots = cartItems.stream()
                .map(item -> createSnapshot(item, context))
                .toList();
        int merchandiseAmount = calculateMerchandiseAmount(snapshots);

        // 4. 외부 노출 식별자를 생성한다. 비회원 조회 토큰은 원문 대신 해시만 DB에 저장한다.
        String orderNumber = generateUniqueOrderNumber();
        OrderIdentifierGenerator.GuestToken guestOrderToken = memberId == null ? generateUniqueGuestToken() : null;
        Order order = orderRepository.save(Order.builder()
                .orderNumber(orderNumber)
                .memberId(memberId)
                .guestTokenHash(guestOrderToken == null ? null : guestOrderToken.hash())
                .ordererName(orderer.name())
                .ordererPhone(orderer.phone())
                .ordererEmail(orderer.email())
                .receiverName(receiver.name())
                .receiverPhone(receiver.phone())
                .zipcode(receiver.zipcode())
                .address(receiver.address())
                .addressDetail(receiver.addressDetail())
                .deliveryMessage(deliveryMessage)
                .merchandiseAmount(merchandiseAmount)
                .discountAmount(0)
                .shippingFee(0)
                .totalAmount(merchandiseAmount)
                .build());

        // 5. 주문 ID가 확정된 뒤 스냅샷을 저장하며 실패 시 주문과 항목을 모두 롤백한다.
        List<OrderItem> orderItems = snapshots.stream()
                .map(snapshot -> toOrderItem(order.getId(), snapshot))
                .toList();
        List<OrderItem> savedItems = orderItemRepository.saveAll(orderItems);
        return toResponse(order, savedItems, guestOrderToken);
    }

    /**
     * 주문 요청의 장바구니 항목 ID 목록을 검증하고 요청 순서를 유지한 목록으로 반환한다.
     *
     * <ul>
     *   <li>항목이 없거나 null이면 거부한다.</li>
     *   <li>{@code MAX_ORDER_LINES} 초과 또는 null 항목이 포함되면 거부한다.</li>
     *   <li>중복 ID가 있으면 거부한다. (사용자가 선택한 순서는 유지된다.)</li>
     * </ul>
     *
     * @param request 주문 생성 요청
     * @return 검증을 통과하고 요청 순서가 유지된 장바구니 항목 ID 목록
     * @throws IllegalArgumentException 검증 실패 시
     */
    private List<Long> validateAndNormalizeItemIds(OrderCreateRequest request) {
        if (request == null || request.cartItemIds() == null || request.cartItemIds().isEmpty()) {
            throw new IllegalArgumentException("주문할 장바구니 상품을 선택해주세요.");
        }

        boolean containsNull = request.cartItemIds().stream().anyMatch(Objects::isNull);
        if (request.cartItemIds().size() > MAX_ORDER_LINES || containsNull) {
            throw new IllegalArgumentException("주문 상품 목록이 올바르지 않습니다.");
        }

        LinkedHashSet<Long> distinctIds = new LinkedHashSet<>(request.cartItemIds());
        if (distinctIds.size() != request.cartItemIds().size()) {
            throw new IllegalArgumentException("중복된 장바구니 상품이 포함되어 있습니다.");
        }
        return List.copyOf(distinctIds);
    }

    /**
     * 요청자 소유의 장바구니를 비관적 쓰기 잠금으로 조회한다.
     * 잠금을 통해 주문 생성 도중 다른 트랜잭션이 장바구니 수량을 변경하는 것을 방지한다.
     *
     * <ul>
     *   <li>회원: {@code memberId}로 장바구니를 조회한다.</li>
     *   <li>비회원: {@code guestCartTokenHash}로 장바구니를 조회하며, 만료된 장바구니는 거부한다.</li>
     * </ul>
     *
     * @param memberId           인증된 회원 ID. 비회원은 {@code null}
     * @param guestCartTokenHash 비회원 장바구니 식별용 SHA-256 토큰 해시. 회원은 {@code null}
     * @return 잠금이 걸린 요청자 소유의 장바구니
     * @throws IllegalArgumentException 비회원인데 토큰 해시가 없을 때
     * @throws ResourceNotFoundException 장바구니가 없거나 만료된 경우
     */
    private Cart requireOwnedCart(Long memberId, String guestCartTokenHash) {
        if (memberId != null) {
            // 수량 변경 API도 같은 잠금을 사용하므로 주문 생성 중 장바구니 변경을 막는다.
            return cartRepository.findByMemberIdForUpdate(memberId)
                    .orElseThrow(() -> new ResourceNotFoundException("장바구니를 찾을 수 없습니다."));
        }
        if (guestCartTokenHash == null) {
            throw new IllegalArgumentException("비회원 장바구니 식별 정보가 필요합니다.");
        }
        Cart cart = cartRepository.findByGuestTokenHashForUpdate(guestCartTokenHash)
                .orElseThrow(() -> new ResourceNotFoundException("장바구니를 찾을 수 없습니다."));
        if (cart.isExpired(LocalDateTime.now())) {
            throw new ResourceNotFoundException("장바구니가 만료되었습니다.");
        }
        return cart;
    }

    private List<CartItem> requireOwnedItems(Long cartId, List<Long> requestedIds) {

        Map<Long, CartItem> itemMap = cartItemRepository.findAllByCartIdAndIdIn(cartId, requestedIds)
                .stream()
                .collect(Collectors.toMap(CartItem::getId, Function.identity()));

        // 개수가 다르면 타 장바구니 ID 또는 이미 삭제된 항목이 섞인 요청이다.
        if (itemMap.size() != requestedIds.size()) {
            throw new ResourceNotFoundException("주문할 장바구니 상품을 찾을 수 없습니다.");
        }

        // DB 조회 순서 대신 사용자가 선택한 순서를 유지해 응답 순서를 예측 가능하게 함.
        return requestedIds.stream().map(itemMap::get).toList();
    }

    private OrdererSnapshot resolveOrderer(Long memberId, OrderCreateRequest.Orderer requestOrderer) {
        // 회원일 경우 정상 회원인지 검증
        if (memberId != null) {
            // 회원 주문은 조작 가능한 요청값 대신 회원 원본 정보를 주문 당시 값으로 복사한다.
            if (requestOrderer != null) {
                throw new IllegalArgumentException("회원 주문에는 주문자 정보를 직접 입력할 수 없습니다.");
            }

            Member member = memberRepository.findById(memberId).orElseThrow(() -> new AuthenticationException("회원 정보를 찾을 수 없습니다. 다시 로그인해주세요."));
            if (!member.isActive()) {
                throw new AuthenticationException("이용할 수 없는 회원 계정입니다.");
            }

            return new OrdererSnapshot(member.getName(), member.getPhone(), member.getEmail());
        }

        // 비회원일 경우 주문자 정보 검증
        if (requestOrderer == null) {
            throw new IllegalArgumentException("비회원 주문자 정보를 입력해주세요.");
        }
        return new OrdererSnapshot(
                requireText(requestOrderer.name(), 50, "주문자명을 입력해주세요.", "주문자명은 50자 이하여야 합니다."),
                requireText(requestOrderer.phone(), 20, "주문자 연락처를 입력해주세요.", "주문자 연락처는 20자 이하여야 합니다."),
                validateEmail(requestOrderer.email()));
    }

    private ReceiverSnapshot validateReceiver(OrderCreateRequest.Receiver receiver) {
        if (receiver == null) {
            throw new IllegalArgumentException("수령인 정보를 입력해주세요.");
        }
        return new ReceiverSnapshot(
                requireText(receiver.name(), 50, "수령인명을 입력해주세요.", "수령인명은 50자 이하여야 합니다."),
                requireText(receiver.phone(), 20, "수령인 연락처를 입력해주세요.", "수령인 연락처는 20자 이하여야 합니다."),
                requireText(receiver.zipcode(), 10, "우편번호를 입력해주세요.", "우편번호는 10자 이하여야 합니다."),
                requireText(receiver.address(), 255, "주소를 입력해주세요.", "주소는 255자 이하여야 합니다."),
                normalizeOptional(receiver.addressDetail(), 255, "상세주소는 255자 이하여야 합니다."));
    }

    /**
     * 장바구니에 담긴 당시 가격이 아닌 현재 판매 정보를 스냅샷 검증에 사용한다.
     *
     * @param cartItems 주문 대상 장바구니 항목 목록
     * @return 스냅샷 생성에 필요한 상품·옵션·카테고리 맵과 옵션 보유 상품 ID 집합
     */
    private SnapshotContext loadSnapshotContext(List<CartItem> cartItems) {
        Set<Long> productIds = cartItems.stream()
                .map(CartItem::getProductId)
                .collect(Collectors.toSet());
        Set<Long> optionIds = cartItems.stream()
                .map(CartItem::getProductOptionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 주문 항목별 개별 조회를 피하도록 상품·옵션·카테고리를 종류별로 한 번씩 조회한다.
        Map<Long, Product> products = toIdMap(productRepository.findAllById(productIds), Product::getId);
        Map<Long, ProductOption> options = optionIds.isEmpty()
                ? Map.of() : toIdMap(productOptionRepository.findAllById(optionIds), ProductOption::getId);
        Set<Long> productIdsWithOptions = new LinkedHashSet<>(productOptionRepository.findProductIdsWithOptions(productIds));
        Set<Long> categoryIds = products.values().stream()
                .map(Product::getCategoryId)
                .collect(Collectors.toSet());
        Map<Long, Category> categories = toIdMap(categoryRepository.findAllById(categoryIds), Category::getId);

        return new SnapshotContext(products, options, categories, productIdsWithOptions);
    }

    private ItemSnapshot createSnapshot(CartItem cartItem, SnapshotContext context) {
        Product product = context.products().get(cartItem.getProductId());
        if (product == null) {
            throw new ResourceNotFoundException("상품을 찾을 수 없습니다.");
        }
        if (!product.isOnSale()) {
            throw new ConflictException("현재 판매 중이 아닌 상품이 포함되어 있습니다.");
        }

        ProductOption option = null;
        int stockQuantity = product.getStockQuantity();
        // 할인가가 있으면 할인가를 기준으로 하고 선택 옵션의 추가금액을 더한다.
        int unitPrice = product.getDisplayPrice();
        if (cartItem.getProductOptionId() != null) {
            option = context.options().get(cartItem.getProductOptionId());
            if (option == null) {
                throw new ResourceNotFoundException("상품 옵션을 찾을 수 없습니다.");
            }
            if (!option.getProductId().equals(product.getId())) {
                throw new IllegalArgumentException("상품에 속하지 않은 옵션이 포함되어 있습니다.");
            }
            if (!Boolean.TRUE.equals(option.getIsActive())) {
                throw new ConflictException("현재 선택할 수 없는 상품 옵션이 포함되어 있습니다.");
            }
            stockQuantity = option.getStockQuantity();
            unitPrice = safeAdd(unitPrice, option.getExtraPrice());
        } else if (context.productIdsWithOptions().contains(product.getId())) {
            // 장바구니에 담은 뒤 옵션 구성이 추가된 경우 잘못된 단일 상품 주문을 차단한다.
            throw new ConflictException("상품 옵션을 다시 선택해주세요.");
        }

        int quantity = cartItem.getQuantity();
        if (quantity < 1 || quantity > MAX_QUANTITY) {
            throw new IllegalArgumentException("주문수량은 1개 이상 99개 이하여야 합니다.");
        }
        if (stockQuantity < quantity) {
            throw new ConflictException("재고가 부족한 상품이 포함되어 있습니다.");
        }

        Category category = context.categories().get(product.getCategoryId());
        if (category == null) {
            throw new ResourceNotFoundException("상품 카테고리를 찾을 수 없습니다.");
        }
        int lineAmount = safeMultiply(unitPrice, quantity);
        return new ItemSnapshot(
                product.getId(),
                option == null ? null : option.getId(),
                product.getName(),
                category.getId(),
                category.getName(),
                option == null ? null : option.getName(),
                product.getThumbnailUrl(),
                unitPrice,
                quantity,
                lineAmount);
    }

    private int calculateMerchandiseAmount(List<ItemSnapshot> snapshots) {
        int amount = 0;
        for (ItemSnapshot snapshot : snapshots) {
            amount = safeAdd(amount, snapshot.lineAmount());
        }
        return amount;
    }

    private String generateUniqueOrderNumber() {
        // DB UNIQUE 제약에 도달하기 전에 제한 횟수만 재생성
        for (int attempt = 0; attempt < IDENTIFIER_GENERATION_ATTEMPTS; attempt++) {
            String orderNumber = identifierGenerator.generateOrderNumber();
            if (!orderRepository.existsByOrderNumber(orderNumber)) {
                return orderNumber;
            }
        }
        throw new IllegalStateException("주문번호를 생성할 수 없습니다.");
    }

    private OrderIdentifierGenerator.GuestToken generateUniqueGuestToken() {
        // 주문별 UNIQUE 컬럼이므로 장바구니 토큰을 재사용하지 않고 새 조회 토큰을 발급
        for (int attempt = 0; attempt < IDENTIFIER_GENERATION_ATTEMPTS; attempt++) {
            OrderIdentifierGenerator.GuestToken token = identifierGenerator.generateGuestToken();
            if (!orderRepository.existsByGuestTokenHash(token.hash())) {
                return token;
            }
        }
        throw new IllegalStateException("비회원 주문 조회 토큰을 생성할 수 없습니다.");
    }

    private OrderItem toOrderItem(Long orderId, ItemSnapshot snapshot) {
        return OrderItem.builder()
                .orderId(orderId)
                .productId(snapshot.productId())
                .productOptionId(snapshot.optionId())
                .productName(snapshot.productName())
                .categoryId(snapshot.categoryId())
                .categoryName(snapshot.categoryName())
                .optionName(snapshot.optionName())
                .thumbnailUrl(snapshot.thumbnailUrl())
                .unitPrice(snapshot.unitPrice())
                .quantity(snapshot.quantity())
                .lineAmount(snapshot.lineAmount())
                .build();
    }

    private OrderCreateResponse toResponse(Order order, List<OrderItem> orderItems, OrderIdentifierGenerator.GuestToken guestOrderToken) {
        List<OrderCreateResponse.Item> items = orderItems.stream()
                .map(item -> new OrderCreateResponse.Item(
                        item.getId(),
                        item.getProductId(),
                        item.getProductOptionId(),
                        item.getProductName(),
                        item.getCategoryName(),
                        item.getOptionName(),
                        item.getThumbnailUrl(),
                        item.getUnitPrice(),
                        item.getQuantity(),
                        item.getLineAmount()))
                .toList();
        return new OrderCreateResponse(
                order.getOrderNumber(),
                order.getStatus(),
                order.getMerchandiseAmount(),
                order.getDiscountAmount(),
                order.getShippingFee(),
                order.getTotalAmount(),
                items,
                guestOrderToken == null ? null : guestOrderToken.rawToken());
    }

    private String requireText(String value, int maximumLength, String requiredMessage, String lengthMessage) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(requiredMessage);
        }
        String normalized = value.trim();
        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException(lengthMessage);
        }
        return normalized;
    }

    private String normalizeOptional(String value, int maximumLength, String lengthMessage) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException(lengthMessage);
        }
        return normalized;
    }

    private String validateEmail(String email) {
        String normalized = normalizeOptional(email, 255, "이메일은 255자 이하여야 합니다.");
        if (normalized != null && !EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("올바른 이메일 형식이 아닙니다.");
        }
        return normalized;
    }

    private int safeAdd(int first, int second) {
        try {
            return Math.addExact(first, second);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("주문금액이 허용 범위를 초과했습니다.");
        }
    }

    private int safeMultiply(int first, int second) {
        try {
            return Math.multiplyExact(first, second);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("주문금액이 허용 범위를 초과했습니다.");
        }
    }

    private <T> Map<Long, T> toIdMap(List<T> values, Function<T, Long> idExtractor) {
        Map<Long, T> result = new HashMap<>();
        for (T value : values) {
            result.put(idExtractor.apply(value), value);
        }
        return result;
    }

    private record OrdererSnapshot(String name, String phone, String email) {}

    private record ReceiverSnapshot(
            String name,
            String phone,
            String zipcode,
            String address,
            String addressDetail
    ) {}

    private record SnapshotContext(
            Map<Long, Product> products,
            Map<Long, ProductOption> options,
            Map<Long, Category> categories,
            Set<Long> productIdsWithOptions
    ) {}

    private record ItemSnapshot(
            Long productId,
            Long optionId,
            String productName,
            Long categoryId,
            String categoryName,
            String optionName,
            String thumbnailUrl,
            Integer unitPrice,
            Integer quantity,
            Integer lineAmount
    ) {}

}
