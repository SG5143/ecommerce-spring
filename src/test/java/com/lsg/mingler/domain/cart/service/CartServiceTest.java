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
import com.lsg.mingler.global.error.ConflictException;
import com.lsg.mingler.global.error.ResourceNotFoundException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductOptionRepository productOptionRepository;

    @Mock
    private CartExpirationService cartExpirationService;

    @InjectMocks
    private CartService cartService;

    @Test
    void 비회원도_숨김_기본옵션_상품을_장바구니에_담을_수_있다() {
        Cart cart = guestCart(1L);
        Product product = product(10L, 12000, 5);
        ProductOption option = defaultOption(20L, 10L, 5);
        AtomicReference<CartItem> savedItem = new AtomicReference<>();
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(productRepository.findAllById(Set.of(10L))).thenReturn(List.of(product));
        when(productOptionRepository.findById(20L)).thenReturn(Optional.of(option));
        when(productOptionRepository.findAllById(Set.of(20L))).thenReturn(List.of(option));
        when(cartRepository.findByGuestTokenHashForUpdate("hash")).thenReturn(Optional.of(cart));
        when(cartItemRepository.findAllByCartIdOrderByCreatedAtAscIdAsc(1L))
                .thenAnswer(invocation -> savedItem.get() == null ? List.of() : List.of(savedItem.get()));
        when(cartItemRepository.findVariant(1L, 10L, 20L)).thenReturn(Optional.empty());
        when(cartItemRepository.save(any(CartItem.class))).thenAnswer(invocation -> {
            CartItem item = invocation.getArgument(0);
            ReflectionTestUtils.setField(item, "id", 100L);
            savedItem.set(item);
            return item;
        });

        CartResponse response = cartService.addItems(null, "hash",
                new CartItemsAddRequest(List.of(new CartItemsAddRequest.Item(10L, 20L, 2))));

        assertThat(response.totalQuantity()).isEqualTo(2);
        assertThat(response.merchandiseTotal()).isEqualTo(24000);
        assertThat(savedItem.get().getUnitPriceAtAdded()).isEqualTo(12000);
    }

    @Test
    void 옵션ID_없이_장바구니에_담을_수_없다() {
        assertThatThrownBy(() -> cartService.addItems(7L, null,
                new CartItemsAddRequest(List.of(new CartItemsAddRequest.Item(10L, null, 1)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("상품 정보가 올바르지 않습니다");

        verifyNoInteractions(productRepository, productOptionRepository, cartRepository);
    }

    @Test
    void 같은_상품옵션을_다시_담으면_새_행이_아닌_수량을_합산한다() {
        Cart cart = memberCart(1L, 7L);
        Product product = product(10L, 10000, 20);
        ProductOption option = option(20L, 10L, 1000, 8, true);
        CartItem existing = cartItem(100L, 1L, 10L, 20L, 2, 11000);
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(productRepository.findAllById(Set.of(10L))).thenReturn(List.of(product));
        when(productOptionRepository.findById(20L)).thenReturn(Optional.of(option));
        when(productOptionRepository.findAllById(Set.of(20L))).thenReturn(List.of(option));
        when(cartRepository.findByMemberIdForUpdate(7L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findAllByCartIdOrderByCreatedAtAscIdAsc(1L)).thenReturn(List.of(existing));
        when(cartItemRepository.findVariant(1L, 10L, 20L)).thenReturn(Optional.of(existing));
        when(cartItemRepository.save(existing)).thenReturn(existing);

        CartResponse response = cartService.addItems(7L, null,
                new CartItemsAddRequest(List.of(new CartItemsAddRequest.Item(10L, 20L, 3))));

        assertThat(existing.getQuantity()).isEqualTo(5);
        assertThat(response.totalQuantity()).isEqualTo(5);
        verify(cartItemRepository).save(existing);
    }

    @Test
    void 상품에_속하지_않은_옵션은_담을_수_없다() {
        Product product = product(10L, 10000, 20);
        ProductOption otherProductOption = option(20L, 99L, 0, 10, true);
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(productOptionRepository.findById(20L)).thenReturn(Optional.of(otherProductOption));

        assertThatThrownBy(() -> cartService.addItems(7L, null,
                new CartItemsAddRequest(List.of(new CartItemsAddRequest.Item(10L, 20L, 1)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("속하지 않은 옵션");

        verify(cartRepository, never()).findByMemberIdForUpdate(any());
    }

    @Test
    void 현재_재고보다_많은_수량은_409를_반환한다() {
        Product product = product(10L, 10000, 2);
        ProductOption option = defaultOption(20L, 10L, 2);
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(productOptionRepository.findById(20L)).thenReturn(Optional.of(option));

        assertThatThrownBy(() -> cartService.addItems(7L, null,
                new CartItemsAddRequest(List.of(new CartItemsAddRequest.Item(10L, 20L, 3)))))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("재고가 부족합니다");
    }

    @Test
    void 다른_장바구니의_항목은_수정할_수_없다() {
        Cart cart = memberCart(1L, 7L);
        when(cartRepository.findByMemberIdForUpdate(7L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdAndId(1L, 999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.updateQuantity(7L, null, 999L, 2))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("장바구니 상품을 찾을 수 없습니다");
    }

    @Test
    void 여러_장바구니_항목을_삭제할_때_소유권을_한_번에_조회한다() {
        Cart cart = memberCart(1L, 7L);
        CartItem firstItem = cartItem(100L, 1L, 10L, 20L, 1, 10000);
        CartItem secondItem = cartItem(101L, 1L, 11L, 21L, 1, 12000);
        when(cartRepository.findByMemberIdForUpdate(7L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findAllByCartIdAndIdIn(1L, Set.of(100L, 101L)))
                .thenReturn(List.of(firstItem, secondItem));
        when(cartItemRepository.findAllByCartIdOrderByCreatedAtAscIdAsc(1L)).thenReturn(List.of());

        CartResponse response = cartService.deleteItems(7L, null, List.of(100L, 101L));

        assertThat(response.items()).isEmpty();
        verify(cartItemRepository).findAllByCartIdAndIdIn(1L, Set.of(100L, 101L));
        verify(cartItemRepository).deleteAllByCartIdAndIdIn(1L, Set.of(100L, 101L));
        verify(cartItemRepository, never()).findByCartIdAndId(any(), any());
    }

    @Test
    void 삭제_항목_중_현재_장바구니에_없는_항목이_있으면_삭제하지_않는다() {
        Cart cart = memberCart(1L, 7L);
        CartItem ownedItem = cartItem(100L, 1L, 10L, 20L, 1, 10000);
        when(cartRepository.findByMemberIdForUpdate(7L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findAllByCartIdAndIdIn(1L, Set.of(100L, 999L)))
                .thenReturn(List.of(ownedItem));

        assertThatThrownBy(() -> cartService.deleteItems(7L, null, List.of(100L, 999L)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("장바구니 상품을 찾을 수 없습니다");

        verify(cartItemRepository, never()).deleteAllByCartIdAndIdIn(any(), any());
    }

    @Test
    void 중복된_삭제_항목_ID는_한_번만_검증하고_삭제한다() {
        Cart cart = memberCart(1L, 7L);
        CartItem item = cartItem(100L, 1L, 10L, 20L, 1, 10000);
        when(cartRepository.findByMemberIdForUpdate(7L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findAllByCartIdAndIdIn(1L, Set.of(100L))).thenReturn(List.of(item));
        when(cartItemRepository.findAllByCartIdOrderByCreatedAtAscIdAsc(1L)).thenReturn(List.of());

        cartService.deleteItems(7L, null, List.of(100L, 100L));

        verify(cartItemRepository).findAllByCartIdAndIdIn(1L, Set.of(100L));
        verify(cartItemRepository).deleteAllByCartIdAndIdIn(1L, Set.of(100L));
    }

    @Test
    void 로그인_병합시_중복옵션_수량은_재고까지_합산된다() {
        Cart guestCart = guestCart(1L);
        Cart memberCart = memberCart(2L, 7L);
        Product product = product(10L, 10000, 20);
        ProductOption option = option(20L, 10L, 1000, 5, true);
        CartItem guestItem = cartItem(101L, 1L, 10L, 20L, 4, 11000);
        CartItem memberItem = cartItem(102L, 2L, 10L, 20L, 3, 11000);
        when(cartRepository.findByGuestTokenHashForUpdate("hash")).thenReturn(Optional.of(guestCart));
        when(cartRepository.findByMemberIdForUpdate(7L)).thenReturn(Optional.of(memberCart));
        when(cartItemRepository.findAllByCartIdOrderByCreatedAtAscIdAsc(1L)).thenReturn(List.of(guestItem));
        when(cartItemRepository.findAllByCartIdOrderByCreatedAtAscIdAsc(2L)).thenReturn(List.of(memberItem));
        when(cartItemRepository.findVariant(2L, 10L, 20L)).thenReturn(Optional.of(memberItem));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(productRepository.findAllById(Set.of(10L))).thenReturn(List.of(product));
        when(productOptionRepository.findById(20L)).thenReturn(Optional.of(option));
        when(productOptionRepository.findAllById(Set.of(20L))).thenReturn(List.of(option));
        when(cartItemRepository.save(memberItem)).thenReturn(memberItem);

        CartMergeResponse response = cartService.mergeGuestCart(7L, "hash");

        assertThat(memberItem.getQuantity()).isEqualTo(5);
        assertThat(response.mergedItemCount()).isEqualTo(1);
        assertThat(response.adjustedItemIds()).containsExactly(102L);
        verify(cartRepository).delete(guestCart);
    }

    @Test
    void 장바구니_응답은_상품과_옵션을_중복_없이_일괄_조회한다() {
        Cart cart = memberCart(1L, 7L);
        Product firstProduct = product(10L, 10000, 10);
        Product secondProduct = product(11L, 12000, 10);
        ProductOption option = option(20L, 10L, 1000, 10, true);
        CartItem firstItem = cartItem(100L, 1L, 10L, 20L, 1, 11000);
        CartItem secondItem = cartItem(101L, 1L, 10L, 20L, 2, 11000);
        ProductOption defaultOption = defaultOption(21L, 11L, 0);
        CartItem optionlessItem = cartItem(102L, 1L, 11L, 21L, 1, 12000);
        when(cartRepository.findByMemberId(7L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findAllByCartIdOrderByCreatedAtAscIdAsc(1L))
                .thenReturn(List.of(firstItem, secondItem, optionlessItem));
        when(productRepository.findAllById(Set.of(10L, 11L)))
                .thenReturn(List.of(firstProduct, secondProduct));
        when(productOptionRepository.findAllById(Set.of(20L, 21L))).thenReturn(List.of(option, defaultOption));

        CartResponse response = cartService.getCart(7L, null);

        assertThat(response.items()).hasSize(3);
        assertThat(response.items().get(2).optionName()).isNull();
        verify(productRepository).findAllById(Set.of(10L, 11L));
        verify(productOptionRepository).findAllById(Set.of(20L, 21L));
        verify(productRepository, never()).findById(any());
        verify(productOptionRepository, never()).findById(any());
    }

    @Test
    void 일괄_조회에서_상품이나_선택_옵션이_없어도_기존_판매불가_사유를_유지한다() {
        Cart cart = memberCart(1L, 7L);
        Product product = product(10L, 10000, 10);
        CartItem missingProductItem = cartItem(100L, 1L, 99L, 998L, 1, 10000);
        CartItem missingOptionItem = cartItem(101L, 1L, 10L, 999L, 1, 10000);
        when(cartRepository.findByMemberId(7L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findAllByCartIdOrderByCreatedAtAscIdAsc(1L))
                .thenReturn(List.of(missingProductItem, missingOptionItem));
        when(productRepository.findAllById(Set.of(99L, 10L))).thenReturn(List.of(product));
        when(productOptionRepository.findAllById(Set.of(998L, 999L))).thenReturn(List.of());

        CartResponse response = cartService.getCart(7L, null);

        assertThat(response.items().get(0).unavailableReason()).isEqualTo("판매 중지된 상품입니다.");
        assertThat(response.items().get(1).unavailableReason()).isEqualTo("현재 선택할 수 없는 옵션입니다.");
    }

    @Test
    void 비회원_쿠키가_없으면_조회만으로_장바구니를_생성하지_않는다() {
        CartResponse response = cartService.getCart(null, null);

        assertThat(response.items()).isEmpty();
        verify(cartRepository, never()).save(any());
        verify(cartRepository, never()).createGuestCartIfAbsent(any(), any());
    }

    @Test
    void 장바구니_조회_메서드는_읽기_전용_트랜잭션을_사용한다() throws NoSuchMethodException {
        Transactional cartTransactional = CartService.class
                .getDeclaredMethod("getCart", Long.class, String.class)
                .getAnnotation(Transactional.class);
        Transactional countTransactional = CartService.class
                .getDeclaredMethod("getCartCount", Long.class, String.class)
                .getAnnotation(Transactional.class);

        assertThat(cartTransactional).isNotNull();
        assertThat(cartTransactional.readOnly()).isTrue();
        assertThat(countTransactional).isNotNull();
        assertThat(countTransactional.readOnly()).isTrue();
    }

    @Test
    void 장바구니_전체_응답_없이_저장된_상품_수량_합계만_조회한다() {
        Cart cart = memberCart(1L, 7L);
        when(cartRepository.findByMemberId(7L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.sumQuantityByCartId(1L)).thenReturn(7L);

        int count = cartService.getCartCount(7L, null);

        assertThat(count).isEqualTo(7);
        verify(cartItemRepository).sumQuantityByCartId(1L);
        verify(cartItemRepository, never()).findAllByCartIdOrderByCreatedAtAscIdAsc(any());
        verify(productRepository, never()).findAllById(any());
        verify(productOptionRepository, never()).findAllById(any());
    }

    @Test
    void 장바구니_식별_정보가_없으면_상품_수량은_0이다() {
        int count = cartService.getCartCount(null, null);

        assertThat(count).isZero();
        verify(cartItemRepository, never()).sumQuantityByCartId(any());
    }

    @Test
    void 만료된_비회원_장바구니의_상품_수량은_0이고_별도_트랜잭션으로_정리한다() {
        Cart expiredCart = expiredGuestCart(1L);
        when(cartRepository.findByGuestTokenHash("hash")).thenReturn(Optional.of(expiredCart));

        int count = cartService.getCartCount(null, "hash");

        assertThat(count).isZero();
        verify(cartExpirationService).deleteExpiredGuestCart(eq(1L), any(LocalDateTime.class));
        verify(cartItemRepository, never()).sumQuantityByCartId(any());
    }

    @Test
    void 조회한_비회원_장바구니가_만료됐으면_별도_트랜잭션으로_삭제한다() {
        Cart expiredCart = expiredGuestCart(1L);
        when(cartRepository.findByGuestTokenHash("hash")).thenReturn(Optional.of(expiredCart));

        CartResponse response = cartService.getCart(null, "hash");

        assertThat(response.items()).isEmpty();
        verify(cartExpirationService).deleteExpiredGuestCart(eq(1L), any(LocalDateTime.class));
        verify(cartRepository, never()).delete(any(Cart.class));
    }

    @Test
    void 잠금_조회한_만료_장바구니는_현재_쓰기_트랜잭션에서_삭제한다() {
        Cart expiredCart = expiredGuestCart(1L);
        when(cartRepository.findByGuestTokenHashForUpdate("hash")).thenReturn(Optional.of(expiredCart));

        assertThatThrownBy(() -> cartService.updateQuantity(null, "hash", 100L, 2))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("장바구니를 찾을 수 없습니다");

        verify(cartRepository).delete(expiredCart);
        verify(cartExpirationService, never()).deleteExpiredGuestCart(any(), any());
    }

    private Product product(Long id, int price, int ignoredStock) {
        Product product = Product.builder()
                .categoryId(1L)
                .name("테스트 상품")
                .price(price)
                .build();
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }

    private ProductOption option(Long id, Long productId, int extraPrice, int stock, boolean active) {
        ProductOption option = ProductOption.builder()
                .productId(productId)
                .name("M")
                .extraPrice(extraPrice)
                .stockQuantity(stock)
                .isActive(active)
                .build();
        ReflectionTestUtils.setField(option, "id", id);
        return option;
    }

    private ProductOption defaultOption(Long id, Long productId, int stock) {
        ProductOption option = option(id, productId, 0, stock, true);
        ReflectionTestUtils.setField(option, "isDefault", true);
        return option;
    }

    private Cart guestCart(Long id) {
        Cart cart = Cart.builder()
                .guestTokenHash("hash")
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();
        ReflectionTestUtils.setField(cart, "id", id);
        return cart;
    }

    private Cart expiredGuestCart(Long id) {
        Cart cart = Cart.builder()
                .guestTokenHash("hash")
                .expiresAt(LocalDateTime.now().minusDays(1))
                .build();
        ReflectionTestUtils.setField(cart, "id", id);
        return cart;
    }

    private Cart memberCart(Long id, Long memberId) {
        Cart cart = Cart.builder().memberId(memberId).build();
        ReflectionTestUtils.setField(cart, "id", id);
        return cart;
    }

    private CartItem cartItem(Long id, Long cartId, Long productId, Long optionId,
                              int quantity, int price) {
        CartItem item = CartItem.builder()
                .cartId(cartId)
                .productId(productId)
                .productOptionId(optionId)
                .quantity(quantity)
                .unitPriceAtAdded(price)
                .build();
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }
}
