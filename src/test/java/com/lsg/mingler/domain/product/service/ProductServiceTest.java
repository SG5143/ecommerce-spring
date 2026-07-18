package com.lsg.mingler.domain.product.service;

import com.lsg.mingler.domain.product.dao.ProductRepository;
import com.lsg.mingler.domain.product.dto.ProductCard;
import com.lsg.mingler.domain.product.entity.Product;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    private Product sampleProduct(String name, Integer price, Integer salePrice, String thumbnailUrl) {
        return Product.builder()
                .categoryId(3L)
                .name(name)
                .description("설명")
                .price(price)
                .salePrice(salePrice)
                .stockQuantity(10)
                .thumbnailUrl(thumbnailUrl)
                .build();
    }

    @Test
    void 인기상품은_판매량순_상위3개를_판매중_상태로_조회한다() {
        when(productRepository.findTop3ByStatusOrderBySalesCountDescIdDesc("ON_SALE"))
                .thenReturn(List.of());

        productService.getPopularProducts();

        verify(productRepository).findTop3ByStatusOrderBySalesCountDescIdDesc("ON_SALE");
    }

    @Test
    void 신상품은_최신순_상위3개를_판매중_상태로_조회한다() {
        when(productRepository.findTop3ByStatusOrderByCreatedAtDescIdDesc("ON_SALE"))
                .thenReturn(List.of());

        productService.getNewProducts();

        verify(productRepository).findTop3ByStatusOrderByCreatedAtDescIdDesc("ON_SALE");
    }

    @Test
    void 할인가가_있으면_세일_상태와_할인가_할인율이_카드에_담긴다() {
        Product product = sampleProduct("트래블 더플백", 89000, 71000, "https://placehold.co/300x300?text=Dufflebag");
        when(productRepository.findTop3ByStatusOrderBySalesCountDescIdDesc("ON_SALE"))
                .thenReturn(List.of(product));

        List<ProductCard> cards = productService.getPopularProducts();

        assertThat(cards).hasSize(1);
        ProductCard card = cards.get(0);
        assertThat(card.onSale()).isTrue();
        assertThat(card.price()).isEqualTo(89000);
        assertThat(card.salePrice()).isEqualTo(71000);
        assertThat(card.finalPrice()).isEqualTo(71000);
        assertThat(card.discountRate()).isEqualTo(20);
    }

    @Test
    void 할인가가_없으면_세일이_아니고_정가가_최종_가격이_된다() {
        Product product = sampleProduct("울 코트", 158000, null, "https://placehold.co/300x300?text=Coat");
        when(productRepository.findTop3ByStatusOrderBySalesCountDescIdDesc("ON_SALE"))
                .thenReturn(List.of(product));

        List<ProductCard> cards = productService.getPopularProducts();

        ProductCard card = cards.get(0);
        assertThat(card.onSale()).isFalse();
        assertThat(card.salePrice()).isNull();
        assertThat(card.finalPrice()).isEqualTo(158000);
    }

    @Test
    void 상품_이름과_썸네일이_카드에_매핑된다() {
        Product product = sampleProduct("데일리 백팩", 65000, null, "https://placehold.co/300x300?text=Backpack");
        when(productRepository.findTop3ByStatusOrderBySalesCountDescIdDesc("ON_SALE"))
                .thenReturn(List.of(product));

        List<ProductCard> cards = productService.getPopularProducts();

        assertThat(cards.get(0).name()).isEqualTo("데일리 백팩");
        assertThat(cards.get(0).imageUrl()).isEqualTo("https://placehold.co/300x300?text=Backpack");
    }

    @Test
    void 판매중_상품이_없으면_빈_목록을_반환한다() {
        when(productRepository.findTop3ByStatusOrderBySalesCountDescIdDesc("ON_SALE"))
                .thenReturn(List.of());
        when(productRepository.findTop3ByStatusOrderByCreatedAtDescIdDesc("ON_SALE"))
                .thenReturn(List.of());

        assertThat(productService.getPopularProducts()).isEmpty();
        assertThat(productService.getNewProducts()).isEmpty();
    }

}
