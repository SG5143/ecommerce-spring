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
    void 메인화면_상품은_판매중_상태로_조회한다() {
        when(productRepository.findTop8ByStatusOrderByCreatedAtDescIdDesc("ON_SALE"))
                .thenReturn(List.of());

        productService.getMainProducts();

        verify(productRepository).findTop8ByStatusOrderByCreatedAtDescIdDesc("ON_SALE");
    }

    @Test
    void 할인가가_있으면_할인가가_카드_가격이_된다() {
        Product product = sampleProduct("캐시미어 니트", 89000, 69000, "https://placehold.co/300x300?text=Knit");
        when(productRepository.findTop8ByStatusOrderByCreatedAtDescIdDesc("ON_SALE"))
                .thenReturn(List.of(product));

        List<ProductCard> cards = productService.getMainProducts();

        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).price()).isEqualTo(69000);
    }

    @Test
    void 할인가가_없으면_정가가_카드_가격이_된다() {
        Product product = sampleProduct("울 코트", 158000, null, "https://placehold.co/300x300?text=Coat");
        when(productRepository.findTop8ByStatusOrderByCreatedAtDescIdDesc("ON_SALE"))
                .thenReturn(List.of(product));

        List<ProductCard> cards = productService.getMainProducts();

        assertThat(cards.get(0).price()).isEqualTo(158000);
    }

    @Test
    void 상품_이름과_썸네일이_카드에_매핑된다() {
        Product product = sampleProduct("데일리 백팩", 65000, null, "https://placehold.co/300x300?text=Backpack");
        when(productRepository.findTop8ByStatusOrderByCreatedAtDescIdDesc("ON_SALE"))
                .thenReturn(List.of(product));

        List<ProductCard> cards = productService.getMainProducts();

        assertThat(cards.get(0).name()).isEqualTo("데일리 백팩");
        assertThat(cards.get(0).imageUrl()).isEqualTo("https://placehold.co/300x300?text=Backpack");
    }

    @Test
    void 판매중_상품이_없으면_빈_목록을_반환한다() {
        when(productRepository.findTop8ByStatusOrderByCreatedAtDescIdDesc("ON_SALE"))
                .thenReturn(List.of());

        List<ProductCard> cards = productService.getMainProducts();

        assertThat(cards).isEmpty();
    }

}
