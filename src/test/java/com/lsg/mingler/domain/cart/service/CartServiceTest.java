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
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

    @InjectMocks
    private CartService cartService;

    @Test
    void 비회원도_옵션없는_상품을_장바구니에_담을_수_있다() {
        Cart cart = guestCart(1L);
        Product product = product(10L, 12000, 5);
        AtomicReference<CartItem> savedItem = new AtomicReference<>();
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(productOptionRepository.existsByProductId(10L)).thenReturn(false);
        when(cartRepository.findByGuestTokenHashForUpdate("hash")).thenReturn(Optional.of(cart));
        when(cartItemRepository.findAllByCartIdOrderByCreatedAtAscIdAsc(1L))
                .thenAnswer(invocation -> savedItem.get() == null ? List.of() : List.of(savedItem.get()));
        when(cartItemRepository.findVariant(1L, 10L, null)).thenReturn(Optional.empty());
        when(cartItemRepository.save(any(CartItem.class))).thenAnswer(invocation -> {
            CartItem item = invocation.getArgument(0);
            ReflectionTestUtils.setField(item, "id", 100L);
            savedItem.set(item);
            return item;
        });

        CartResponse response = cartService.addItems(null, "hash",
                new CartItemsAddRequest(List.of(new CartItemsAddRequest.Item(10L, null, 2))));

        assertThat(response.totalQuantity()).isEqualTo(2);
        assertThat(response.merchandiseTotal()).isEqualTo(24000);
        assertThat(savedItem.get().getUnitPriceAtAdded()).isEqualTo(12000);
    }

    @Test
    void 같은_상품옵션을_다시_담으면_새_행이_아닌_수량을_합산한다() {
        Cart cart = memberCart(1L, 7L);
        Product product = product(10L, 10000, 20);
        ProductOption option = option(20L, 10L, 1000, 8, true);
        CartItem existing = cartItem(100L, 1L, 10L, 20L, 2, 11000);
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(productOptionRepository.existsByProductId(10L)).thenReturn(true);
        when(productOptionRepository.findById(20L)).thenReturn(Optional.of(option));
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
        when(productOptionRepository.existsByProductId(10L)).thenReturn(true);
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
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(productOptionRepository.existsByProductId(10L)).thenReturn(false);

        assertThatThrownBy(() -> cartService.addItems(7L, null,
                new CartItemsAddRequest(List.of(new CartItemsAddRequest.Item(10L, null, 3)))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("재고가 부족합니다");
    }

    @Test
    void 다른_장바구니의_항목은_수정할_수_없다() {
        Cart cart = memberCart(1L, 7L);
        when(cartRepository.findByMemberIdForUpdate(7L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdAndId(1L, 999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.updateQuantity(7L, null, 999L, 2))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("장바구니 상품을 찾을 수 없습니다");
    }

    @Test
    void 여러_장바구니_항목을_삭제할_때_소유권을_한_번에_조회한다() {
        Cart cart = memberCart(1L, 7L);
        CartItem firstItem = cartItem(100L, 1L, 10L, null, 1, 10000);
        CartItem secondItem = cartItem(101L, 1L, 11L, null, 1, 12000);
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
        CartItem ownedItem = cartItem(100L, 1L, 10L, null, 1, 10000);
        when(cartRepository.findByMemberIdForUpdate(7L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findAllByCartIdAndIdIn(1L, Set.of(100L, 999L)))
                .thenReturn(List.of(ownedItem));

        assertThatThrownBy(() -> cartService.deleteItems(7L, null, List.of(100L, 999L)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("장바구니 상품을 찾을 수 없습니다");

        verify(cartItemRepository, never()).deleteAllByCartIdAndIdIn(any(), any());
    }

    @Test
    void 중복된_삭제_항목_ID는_한_번만_검증하고_삭제한다() {
        Cart cart = memberCart(1L, 7L);
        CartItem item = cartItem(100L, 1L, 10L, null, 1, 10000);
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
        when(productOptionRepository.existsByProductId(10L)).thenReturn(true);
        when(productOptionRepository.findById(20L)).thenReturn(Optional.of(option));
        when(cartItemRepository.save(memberItem)).thenReturn(memberItem);

        CartMergeResponse response = cartService.mergeGuestCart(7L, "hash");

        assertThat(memberItem.getQuantity()).isEqualTo(5);
        assertThat(response.mergedItemCount()).isEqualTo(1);
        assertThat(response.adjustedItemIds()).containsExactly(102L);
        verify(cartRepository).delete(guestCart);
    }

    @Test
    void 비회원_쿠키가_없으면_조회만으로_장바구니를_생성하지_않는다() {
        CartResponse response = cartService.getCart(null, null);

        assertThat(response.items()).isEmpty();
        verify(cartRepository, never()).save(any());
        verify(cartRepository, never()).createGuestCartIfAbsent(any(), any());
    }

    private Product product(Long id, int price, int stock) {
        Product product = Product.builder()
                .categoryId(1L)
                .name("테스트 상품")
                .price(price)
                .stockQuantity(stock)
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

    private Cart guestCart(Long id) {
        Cart cart = Cart.builder()
                .guestTokenHash("hash")
                .expiresAt(java.time.LocalDateTime.now().plusDays(30))
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
