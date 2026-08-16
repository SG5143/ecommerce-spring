package com.lsg.mingler.domain.cart.service;

import com.lsg.mingler.domain.cart.dao.CartItemRepository;
import com.lsg.mingler.domain.cart.dao.CartRepository;
import com.lsg.mingler.domain.cart.dto.CartResponse;
import com.lsg.mingler.domain.cart.entity.Cart;
import com.lsg.mingler.domain.cart.entity.CartItem;
import com.lsg.mingler.domain.member.dao.MemberRepository;
import com.lsg.mingler.domain.member.entity.Member;
import com.lsg.mingler.domain.product.dao.ProductOptionRepository;
import com.lsg.mingler.domain.product.dao.ProductRepository;
import com.lsg.mingler.domain.product.entity.Product;
import com.lsg.mingler.domain.product.entity.ProductOption;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ActiveProfiles("test")
@Transactional
class CartServiceQueryIntegrationTest {

    @Autowired
    private CartService cartService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductOptionRepository productOptionRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void 회원_장바구니_수량변경은_대표흐름에서_다섯개_SQL문을_실행한다() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        LocalDateTime now = LocalDateTime.now();
        Member member = memberRepository.save(Member.builder()
                .username("cart-query-" + suffix)
                .password("encoded-password")
                .name("쿼리 테스트")
                .phone("010" + suffix)
                .birthDate(LocalDate.of(1990, 1, 1))
                .marketingAgreed(false)
                .termsAgreedAt(now)
                .privacyAgreedAt(now)
                .build());
        Product product = productRepository.save(Product.builder()
                .categoryId(3L)
                .name("장바구니 쿼리 테스트 상품 " + suffix)
                .price(10000)
                .build());
        ProductOption option = productOptionRepository.save(ProductOption.builder()
                .productId(product.getId())
                .name("기본 옵션 " + suffix)
                .extraPrice(1000)
                .stockQuantity(10)
                .displayOrder(0)
                .isActive(true)
                .isDefault(false)
                .build());
        Cart cart = cartRepository.save(Cart.builder().memberId(member.getId()).build());
        CartItem item = cartItemRepository.save(CartItem.builder()
                .cartId(cart.getId())
                .productId(product.getId())
                .productOptionId(option.getId())
                .quantity(1)
                .unitPriceAtAdded(11000)
                .build());
        entityManager.flush();
        entityManager.clear();

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        CartResponse response = cartService.updateQuantity(member.getId(), null, item.getId(), 2);
        entityManager.flush();

        assertThat(response.totalQuantity()).isEqualTo(2);
        assertThat(response.merchandiseTotal()).isEqualTo(22000);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(5);
    }
}
