package com.lsg.mingler.domain.product.service;

import com.lsg.mingler.domain.product.dao.ProductImageRepository;
import com.lsg.mingler.domain.product.dao.ProductOptionRepository;
import com.lsg.mingler.domain.product.dao.ProductRepository;
import com.lsg.mingler.domain.product.dto.ProductCard;
import com.lsg.mingler.domain.product.dto.ProductDetail;
import com.lsg.mingler.domain.product.entity.Product;
import com.lsg.mingler.domain.product.entity.ProductImage;
import com.lsg.mingler.domain.product.entity.ProductOption;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductImageRepository productImageRepository;

    @Mock
    private ProductOptionRepository productOptionRepository;

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

    private ProductImage sampleImage(String url, int displayOrder) {
        return ProductImage.builder()
                .productId(1L)
                .imageUrl(url)
                .displayOrder(displayOrder)
                .build();
    }

    private ProductOption sampleOption(String name, int extraPrice, int stockQuantity) {
        return ProductOption.builder()
                .productId(1L)
                .name(name)
                .extraPrice(extraPrice)
                .stockQuantity(stockQuantity)
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
    void 카테고리_상품은_판매중_상태로_최신순_조회한다() {
        when(productRepository.findByCategoryIdInAndStatusOrderByCreatedAtDescIdDesc(List.of(1L, 3L, 4L), "ON_SALE"))
                .thenReturn(List.of(sampleProduct("울 코트", 158000, null, "https://placehold.co/300x300?text=Coat")));

        List<ProductCard> cards = productService.getCategoryProducts(List.of(1L, 3L, 4L));

        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).name()).isEqualTo("울 코트");
        verify(productRepository).findByCategoryIdInAndStatusOrderByCreatedAtDescIdDesc(List.of(1L, 3L, 4L), "ON_SALE");
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
    void 상품_상세는_이미지와_옵션을_정렬된_순서로_담는다() {
        Product product = sampleProduct("캐시미어 블렌드 라운드넥 니트", 89000, 69000, "https://placehold.co/300x300?text=Knit+1");
        when(productRepository.increaseViewCount(1L, Product.STATUS_HIDDEN)).thenReturn(1);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productImageRepository.findAllByProductIdOrderByDisplayOrderAscIdAsc(1L))
                .thenReturn(List.of(sampleImage("https://placehold.co/300x300?text=Knit+1", 0),
                        sampleImage("https://placehold.co/300x300?text=Knit+2", 1)));
        when(productOptionRepository.findAllByProductIdAndIsActiveTrueOrderByDisplayOrderAscIdAsc(1L))
                .thenReturn(List.of(sampleOption("S", 0, 40), sampleOption("L", 1000, 0)));

        ProductDetail detail = productService.getProductDetail(1L);

        assertThat(detail.imageUrls()).containsExactly(
                "https://placehold.co/300x300?text=Knit+1", "https://placehold.co/300x300?text=Knit+2");
        assertThat(detail.hasOptions()).isTrue();
        assertThat(detail.options()).hasSize(2);
        assertThat(detail.options().get(1).extraPrice()).isEqualTo(1000);
        assertThat(detail.options().get(1).soldOut()).isTrue();
        assertThat(detail.finalPrice()).isEqualTo(69000);
        assertThat(detail.soldOut()).isFalse();
    }

    @Test
    void 이미지가_없으면_썸네일이_대표_이미지가_된다() {
        Product product = sampleProduct("울 코트", 158000, null, "https://placehold.co/300x300?text=Coat");
        when(productRepository.increaseViewCount(1L, Product.STATUS_HIDDEN)).thenReturn(1);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productImageRepository.findAllByProductIdOrderByDisplayOrderAscIdAsc(1L)).thenReturn(List.of());
        when(productOptionRepository.findAllByProductIdAndIsActiveTrueOrderByDisplayOrderAscIdAsc(1L))
                .thenReturn(List.of());

        ProductDetail detail = productService.getProductDetail(1L);

        assertThat(detail.imageUrls()).containsExactly("https://placehold.co/300x300?text=Coat");
        assertThat(detail.hasOptions()).isFalse();
    }

    @Test
    void 상세_조회는_조회수를_원자적으로_1_증가시킨다() {
        Product product = sampleProduct("울 코트", 158000, null, "https://placehold.co/300x300?text=Coat");
        when(productRepository.increaseViewCount(1L, Product.STATUS_HIDDEN)).thenReturn(1);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productImageRepository.findAllByProductIdOrderByDisplayOrderAscIdAsc(1L)).thenReturn(List.of());
        when(productOptionRepository.findAllByProductIdAndIsActiveTrueOrderByDisplayOrderAscIdAsc(1L))
                .thenReturn(List.of());

        productService.getProductDetail(1L);

        verify(productRepository).increaseViewCount(1L, Product.STATUS_HIDDEN);
    }

    @Test
    void 품절_상품_상세는_soldOut이_true다() {
        Product product = sampleProduct("린넨 반팔 셔츠", 39000, null, "https://placehold.co/300x300?text=Linen+Shirt+1");
        ReflectionTestUtils.setField(product, "status", Product.STATUS_SOLD_OUT);
        when(productRepository.increaseViewCount(1L, Product.STATUS_HIDDEN)).thenReturn(1);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productImageRepository.findAllByProductIdOrderByDisplayOrderAscIdAsc(1L)).thenReturn(List.of());
        when(productOptionRepository.findAllByProductIdAndIsActiveTrueOrderByDisplayOrderAscIdAsc(1L))
                .thenReturn(List.of());

        ProductDetail detail = productService.getProductDetail(1L);

        assertThat(detail.soldOut()).isTrue();
    }

    @Test
    void 숨김_상품_상세_조회는_404_예외를_던진다() {
        when(productRepository.increaseViewCount(1L, Product.STATUS_HIDDEN)).thenReturn(0);

        assertThatThrownBy(() -> productService.getProductDetail(1L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("상품을 찾을 수 없습니다.");

        verifyNoInteractions(productImageRepository, productOptionRepository);
    }

    @Test
    void 없는_상품_상세_조회는_404_예외를_던진다() {
        when(productRepository.increaseViewCount(999L, Product.STATUS_HIDDEN)).thenReturn(0);

        assertThatThrownBy(() -> productService.getProductDetail(999L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("상품을 찾을 수 없습니다.");

        verifyNoInteractions(productImageRepository, productOptionRepository);
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
