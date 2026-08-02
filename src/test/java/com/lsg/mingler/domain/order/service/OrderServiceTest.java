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
import com.lsg.mingler.domain.order.entity.OrderStatus;
import com.lsg.mingler.domain.product.dao.CategoryRepository;
import com.lsg.mingler.domain.product.dao.ProductOptionRepository;
import com.lsg.mingler.domain.product.dao.ProductRepository;
import com.lsg.mingler.domain.product.entity.Category;
import com.lsg.mingler.domain.product.entity.Product;
import com.lsg.mingler.domain.product.entity.ProductOption;
import com.lsg.mingler.global.error.ConflictException;
import com.lsg.mingler.global.error.ResourceNotFoundException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
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
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductOptionRepository productOptionRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private OrderIdentifierGenerator identifierGenerator;

    @Captor
    private ArgumentCaptor<List<OrderItem>> orderItemsCaptor;

    @Captor
    private ArgumentCaptor<Order> orderCaptor;

    @InjectMocks
    private OrderService orderService;

    @Test
    void 회원_주문은_회원정보와_현재_상품정보를_스냅샷으로_저장한다() {
        Cart cart = memberCart(1L, 7L);
        CartItem cartItem = cartItem(100L, 1L, 10L, 20L, 2);
        Member member = member(7L, "홍길동", "010-1111-2222", "member@example.com");
        Product product = product(10L, 30L, 10000, 9000, 50);
        ProductOption option = option(20L, 10L, "Large", 1000, 10, true);
        Category category = category(30L, "상의");
        stubSnapshotData(cartItem, product, option, category);
        when(cartRepository.findByMemberIdForUpdate(7L)).thenReturn(Optional.of(cart));
        when(memberRepository.findById(7L)).thenReturn(Optional.of(member));
        stubIdentifiersAndSaves();

        OrderCreateResponse response = orderService.createOrder(
                7L, null, request(null));

        assertThat(response.orderNumber()).isEqualTo("ORD-TEST");
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(response.merchandiseAmount()).isEqualTo(20000);
        assertThat(response.discountAmount()).isZero();
        assertThat(response.shippingFee()).isZero();
        assertThat(response.totalAmount()).isEqualTo(20000);
        assertThat(response.guestOrderToken()).isNull();
        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.productName()).isEqualTo("테스트 상품");
            assertThat(item.categoryName()).isEqualTo("상의");
            assertThat(item.optionName()).isEqualTo("Large");
            assertThat(item.unitPrice()).isEqualTo(10000);
            assertThat(item.quantity()).isEqualTo(2);
            assertThat(item.lineAmount()).isEqualTo(20000);
        });
        verify(orderRepository).save(any(Order.class));
        verify(orderItemRepository).saveAll(orderItemsCaptor.capture());
        assertThat(orderItemsCaptor.getValue())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getLineAmount()).isEqualTo(20000);
                    assertThat(item.getSourceCartItemId()).isEqualTo(100L);
                });
        verify(cartItemRepository, never()).save(any());
    }

    @Test
    void 비회원_주문은_입력한_주문자와_새_조회토큰을_저장한다() {
        Cart cart = guestCart(1L, "cart-hash");
        CartItem cartItem = cartItem(100L, 1L, 10L, 20L, 1);
        Product product = product(10L, 30L, 12000, null, 3);
        ProductOption option = option(20L, 10L, "기본 구성", 0, 3, true);
        Category category = category(30L, "상의");
        stubSnapshotData(cartItem, product, option, category);
        when(cartRepository.findByGuestTokenHashForUpdate("cart-hash")).thenReturn(Optional.of(cart));
        when(identifierGenerator.generateOrderNumber()).thenReturn("ORD-TEST");
        when(orderRepository.existsByOrderNumber("ORD-TEST")).thenReturn(false);
        when(identifierGenerator.generateGuestToken())
                .thenReturn(new OrderIdentifierGenerator.GuestToken("raw-order-token", "order-hash"));
        when(orderRepository.existsByGuestTokenHash("order-hash")).thenReturn(false);
        stubOrderSaves();

        OrderCreateResponse response = orderService.createOrder(
                null,
                "cart-hash",
                request(new OrderCreateRequest.Orderer(
                        "비회원", "010-9999-8888", "guest@example.com")));

        assertThat(response.guestOrderToken()).isEqualTo("raw-order-token");
        verify(orderRepository).save(any(Order.class));
        verify(memberRepository, never()).findById(any());
    }

    @Test
    void 회원_즉시구매는_장바구니없이_선택한_모든옵션을_주문한다() {
        Member member = member(7L, "홍길동", "010-1111-2222", "member@example.com");
        Product product = product(10L, 30L, 10000, 9000, 20);
        ProductOption firstOption = option(20L, 10L, "Large", 1000, 10, true);
        ProductOption secondOption = option(21L, 10L, "Small", 0, 10, true);
        Category category = category(30L, "상의");
        when(memberRepository.findById(7L)).thenReturn(Optional.of(member));
        when(productRepository.findAllById(Set.of(10L))).thenReturn(List.of(product));
        when(productOptionRepository.findAllById(Set.of(20L, 21L)))
                .thenReturn(List.of(firstOption, secondOption));
        when(categoryRepository.findAllById(Set.of(30L))).thenReturn(List.of(category));
        stubIdentifiersAndSaves();

        OrderCreateResponse response = orderService.createOrder(
                7L,
                null,
                directRequest(List.of(
                        new OrderCreateRequest.DirectItem(10L, 20L, 2),
                        new OrderCreateRequest.DirectItem(10L, 21L, 1)),
                        null));

        assertThat(response.merchandiseAmount()).isEqualTo(29_000);
        assertThat(response.items()).extracting(OrderCreateResponse.Item::optionId)
                .containsExactly(20L, 21L);
        verify(orderItemRepository).saveAll(orderItemsCaptor.capture());
        assertThat(orderItemsCaptor.getValue())
                .allSatisfy(item -> assertThat(item.getSourceCartItemId()).isNull());
        verify(cartRepository, never()).findByMemberIdForUpdate(any());
        verify(cartItemRepository, never()).findAllByCartIdAndIdIn(any(), any());
    }

    @Test
    void 비회원_즉시구매는_장바구니토큰없이_주문조회토큰을_발급한다() {
        Product product = product(10L, 30L, 12000, null, 3);
        ProductOption option = option(20L, 10L, "기본 구성", 0, 3, true);
        Category category = category(30L, "상의");
        when(productRepository.findAllById(Set.of(10L))).thenReturn(List.of(product));
        when(productOptionRepository.findAllById(Set.of(20L))).thenReturn(List.of(option));
        when(categoryRepository.findAllById(Set.of(30L))).thenReturn(List.of(category));
        when(identifierGenerator.generateOrderNumber()).thenReturn("ORD-TEST");
        when(orderRepository.existsByOrderNumber("ORD-TEST")).thenReturn(false);
        when(identifierGenerator.generateGuestToken())
                .thenReturn(new OrderIdentifierGenerator.GuestToken("raw-order-token", "order-hash"));
        when(orderRepository.existsByGuestTokenHash("order-hash")).thenReturn(false);
        stubOrderSaves();

        OrderCreateResponse response = orderService.createOrder(
                null,
                null,
                directRequest(
                        List.of(new OrderCreateRequest.DirectItem(10L, 20L, 1)),
                        new OrderCreateRequest.Orderer("비회원", "010-9999-8888", null)));

        assertThat(response.guestOrderToken()).isEqualTo("raw-order-token");
        verify(cartRepository, never()).findByGuestTokenHashForUpdate(any());
    }

    @Test
    void 장바구니와_즉시구매항목을_함께_요청하면_거부한다() {
        OrderCreateRequest request = new OrderCreateRequest(
                List.of(100L),
                List.of(new OrderCreateRequest.DirectItem(10L, 20L, 1)),
                null,
                receiver(),
                null);

        assertThatThrownBy(() -> orderService.createOrder(7L, null, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("하나만");

        verify(orderRepository, never()).save(any());
    }

    @Test
    void 주문항목이_모두_비어있으면_거부한다() {
        OrderCreateRequest request = new OrderCreateRequest(
                null, null, null, receiver(), null);

        assertThatThrownBy(() -> orderService.createOrder(7L, null, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("하나만");

        verify(orderRepository, never()).save(any());
    }

    @Test
    void 중복된_즉시구매옵션은_조회전에_거부한다() {
        OrderCreateRequest request = directRequest(List.of(
                new OrderCreateRequest.DirectItem(10L, 20L, 1),
                new OrderCreateRequest.DirectItem(10L, 20L, 2)), null);

        assertThatThrownBy(() -> orderService.createOrder(7L, null, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("중복");

        verify(productRepository, never()).findAllById(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void 즉시구매_옵션이_상품에_속하지_않으면_거부한다() {
        Member member = member(7L, "홍길동", "010-1111-2222", null);
        Product product = product(10L, 30L, 12000, null, 3);
        ProductOption otherProductOption = option(20L, 11L, "다른 상품 옵션", 0, 3, true);
        Category category = category(30L, "상의");
        when(memberRepository.findById(7L)).thenReturn(Optional.of(member));
        when(productRepository.findAllById(Set.of(10L))).thenReturn(List.of(product));
        when(productOptionRepository.findAllById(Set.of(20L))).thenReturn(List.of(otherProductOption));
        when(categoryRepository.findAllById(Set.of(30L))).thenReturn(List.of(category));

        assertThatThrownBy(() -> orderService.createOrder(
                7L,
                null,
                directRequest(List.of(new OrderCreateRequest.DirectItem(10L, 20L, 1)), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("속하지 않은");

        verify(orderRepository, never()).save(any());
    }

    @Test
    void 즉시구매_수량이_현재재고보다_많으면_거부한다() {
        Member member = member(7L, "홍길동", "010-1111-2222", null);
        Product product = product(10L, 30L, 12000, null, 2);
        ProductOption option = option(20L, 10L, "기본 구성", 0, 2, true);
        Category category = category(30L, "상의");
        when(memberRepository.findById(7L)).thenReturn(Optional.of(member));
        when(productRepository.findAllById(Set.of(10L))).thenReturn(List.of(product));
        when(productOptionRepository.findAllById(Set.of(20L))).thenReturn(List.of(option));
        when(categoryRepository.findAllById(Set.of(30L))).thenReturn(List.of(category));

        assertThatThrownBy(() -> orderService.createOrder(
                7L,
                null,
                directRequest(List.of(new OrderCreateRequest.DirectItem(10L, 20L, 3)), null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("재고가 부족");

        verify(orderRepository, never()).save(any());
    }

    @Test
    void 비회원_주문의_입력문자열은_정규화해서_저장한다() {
        Cart cart = guestCart(1L, "cart-hash");
        CartItem cartItem = cartItem(100L, 1L, 10L, 20L, 1);
        Product product = product(10L, 30L, 12000, null, 3);
        ProductOption option = option(20L, 10L, "기본 구성", 0, 3, true);
        Category category = category(30L, "상의");
        stubSnapshotData(cartItem, product, option, category);
        when(cartRepository.findByGuestTokenHashForUpdate("cart-hash")).thenReturn(Optional.of(cart));
        when(identifierGenerator.generateOrderNumber()).thenReturn("ORD-TEST");
        when(orderRepository.existsByOrderNumber("ORD-TEST")).thenReturn(false);
        when(identifierGenerator.generateGuestToken())
                .thenReturn(new OrderIdentifierGenerator.GuestToken("raw-order-token", "order-hash"));
        when(orderRepository.existsByGuestTokenHash("order-hash")).thenReturn(false);
        stubOrderSaves();
        OrderCreateRequest request = new OrderCreateRequest(
                List.of(100L),
                new OrderCreateRequest.Orderer(
                        " 비회원 ",
                        " 010-9999-8888 ",
                        " guest@example.com "),
                new OrderCreateRequest.Receiver(
                        " 수령인 ",
                        " 010-1234-5678 ",
                        " 12345 ",
                        " 서울시 강남구 ",
                        "   "),
                " 문 앞에 놓아주세요. ");

        orderService.createOrder(null, "cart-hash", request);

        verify(orderRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue()).satisfies(order -> {
            assertThat(order.getOrdererName()).isEqualTo("비회원");
            assertThat(order.getOrdererPhone()).isEqualTo("010-9999-8888");
            assertThat(order.getOrdererEmail()).isEqualTo("guest@example.com");
            assertThat(order.getReceiverName()).isEqualTo("수령인");
            assertThat(order.getAddressDetail()).isNull();
            assertThat(order.getDeliveryMessage()).isEqualTo("문 앞에 놓아주세요.");
        });
    }

    @Test
    void 중복된_장바구니_항목은_조회전에_거부한다() {
        OrderCreateRequest request = new OrderCreateRequest(
                List.of(100L, 100L),
                null,
                receiver(),
                null);

        assertThatThrownBy(() -> orderService.createOrder(7L, null, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("중복");

        verify(cartRepository, never()).findByMemberIdForUpdate(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void 다른_장바구니의_항목이_섞이면_주문을_저장하지_않는다() {
        Cart cart = memberCart(1L, 7L);
        when(cartRepository.findByMemberIdForUpdate(7L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findAllByCartIdAndIdIn(1L, List.of(100L))).thenReturn(List.of());

        assertThatThrownBy(() -> orderService.createOrder(7L, null, request(null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("장바구니 상품");

        verify(orderRepository, never()).save(any());
    }

    @Test
    void 현재_재고보다_주문수량이_많으면_409로_거부한다() {
        Cart cart = memberCart(1L, 7L);
        CartItem cartItem = cartItem(100L, 1L, 10L, 20L, 4);
        Member member = member(7L, "홍길동", "010-1111-2222", null);
        Product product = product(10L, 30L, 12000, null, 3);
        ProductOption option = option(20L, 10L, "기본 구성", 0, 3, true);
        Category category = category(30L, "상의");
        stubSnapshotData(cartItem, product, option, category);
        when(cartRepository.findByMemberIdForUpdate(7L)).thenReturn(Optional.of(cart));
        when(memberRepository.findById(7L)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> orderService.createOrder(7L, null, request(null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("재고가 부족");

        verify(orderRepository, never()).save(any());
    }

    @Test
    void 옵션ID가_없는_기존_장바구니항목은_주문으로_전환하지_않는다() {
        Cart cart = memberCart(1L, 7L);
        CartItem cartItem = cartItem(100L, 1L, 10L, null, 1);
        Member member = member(7L, "홍길동", "010-1111-2222", null);
        when(cartRepository.findByMemberIdForUpdate(7L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findAllByCartIdAndIdIn(1L, List.of(100L))).thenReturn(List.of(cartItem));
        when(memberRepository.findById(7L)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> orderService.createOrder(7L, null, request(null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("옵션 정보");

        verify(productRepository, never()).findAllById(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void 항목_금액이_Integer_범위를_넘으면_저장전에_거부한다() {
        Cart cart = memberCart(1L, 7L);
        CartItem cartItem = cartItem(100L, 1L, 10L, 20L, 2);
        Member member = member(7L, "홍길동", "010-1111-2222", null);
        Product product = product(10L, 30L, Integer.MAX_VALUE, null, 2);
        ProductOption option = option(20L, 10L, "기본 구성", 0, 2, true);
        Category category = category(30L, "상의");
        stubSnapshotData(cartItem, product, option, category);
        when(cartRepository.findByMemberIdForUpdate(7L)).thenReturn(Optional.of(cart));
        when(memberRepository.findById(7L)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> orderService.createOrder(7L, null, request(null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("주문금액이 허용 범위를 초과했습니다.");

        verify(orderRepository, never()).save(any());
        verify(orderItemRepository, never()).saveAll(anyList());
    }

    @Test
    void 만료된_비회원_장바구니는_주문할_수_없다() {
        Cart cart = guestCart(1L, "cart-hash");
        ReflectionTestUtils.setField(cart, "expiresAt", LocalDateTime.now().minusMinutes(1));
        when(cartRepository.findByGuestTokenHashForUpdate("cart-hash")).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> orderService.createOrder(
                null, "cart-hash", request(new OrderCreateRequest.Orderer(
                        "비회원", "010-9999-8888", null))))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("만료");

        verify(cartItemRepository, never()).findAllByCartIdAndIdIn(any(), any());
    }

    private void stubSnapshotData(
            CartItem cartItem, Product product, ProductOption option, Category category) {
        when(cartItemRepository.findAllByCartIdAndIdIn(1L, List.of(100L)))
                .thenReturn(List.of(cartItem));
        when(productRepository.findAllById(Set.of(10L))).thenReturn(List.of(product));
        when(productOptionRepository.findAllById(Set.of(20L))).thenReturn(List.of(option));
        when(categoryRepository.findAllById(Set.of(30L))).thenReturn(List.of(category));
    }

    private void stubIdentifiersAndSaves() {
        when(identifierGenerator.generateOrderNumber()).thenReturn("ORD-TEST");
        when(orderRepository.existsByOrderNumber("ORD-TEST")).thenReturn(false);
        stubOrderSaves();
    }

    private void stubOrderSaves() {
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            ReflectionTestUtils.setField(order, "id", 500L);
            return order;
        });
        AtomicLong itemId = new AtomicLong(600L);
        when(orderItemRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<OrderItem> items = invocation.getArgument(0);
            items.forEach(item -> ReflectionTestUtils.setField(item, "id", itemId.getAndIncrement()));
            return items;
        });
    }

    private OrderCreateRequest request(OrderCreateRequest.Orderer orderer) {
        return new OrderCreateRequest(List.of(100L), orderer, receiver(), "문 앞에 놓아주세요.");
    }

    private OrderCreateRequest directRequest(
            List<OrderCreateRequest.DirectItem> directItems,
            OrderCreateRequest.Orderer orderer) {
        return new OrderCreateRequest(
                null, directItems, orderer, receiver(), "문 앞에 놓아주세요.");
    }

    private OrderCreateRequest.Receiver receiver() {
        return new OrderCreateRequest.Receiver(
                "수령인", "010-1234-5678", "12345", "서울시 강남구", "101호");
    }

    private Cart memberCart(Long id, Long memberId) {
        Cart cart = Cart.builder().memberId(memberId).build();
        ReflectionTestUtils.setField(cart, "id", id);
        return cart;
    }

    private Cart guestCart(Long id, String guestTokenHash) {
        Cart cart = Cart.builder()
                .guestTokenHash(guestTokenHash)
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();
        ReflectionTestUtils.setField(cart, "id", id);
        return cart;
    }

    private CartItem cartItem(
            Long id, Long cartId, Long productId, Long optionId, Integer quantity) {
        CartItem item = CartItem.builder()
                .cartId(cartId)
                .productId(productId)
                .productOptionId(optionId)
                .quantity(quantity)
                .unitPriceAtAdded(1)
                .build();
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }

    private Member member(Long id, String name, String phone, String email) {
        Member member = Member.builder()
                .username("member")
                .password("password")
                .name(name)
                .phone(phone)
                .birthDate(LocalDate.of(2000, 1, 1))
                .termsAgreedAt(LocalDateTime.now())
                .privacyAgreedAt(LocalDateTime.now())
                .build();
        ReflectionTestUtils.setField(member, "id", id);
        ReflectionTestUtils.setField(member, "email", email);
        return member;
    }

    private Product product(
            Long id,
            Long categoryId,
            Integer price,
            Integer salePrice,
            Integer ignoredStockQuantity) {
        Product product = Product.builder()
                .categoryId(categoryId)
                .name("테스트 상품")
                .price(price)
                .salePrice(salePrice)
                .thumbnailUrl("/image.jpg")
                .build();
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }

    private ProductOption option(
            Long id,
            Long productId,
            String name,
            Integer extraPrice,
            Integer stockQuantity,
            Boolean isActive) {
        ProductOption option = ProductOption.builder()
                .productId(productId)
                .name(name)
                .extraPrice(extraPrice)
                .stockQuantity(stockQuantity)
                .isActive(isActive)
                .build();
        ReflectionTestUtils.setField(option, "id", id);
        return option;
    }

    private Category category(Long id, String name) {
        Category category = Category.builder().name(name).build();
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }
}
