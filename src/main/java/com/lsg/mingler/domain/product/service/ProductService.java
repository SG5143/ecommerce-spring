package com.lsg.mingler.domain.product.service;

import com.lsg.mingler.domain.product.dao.ProductImageRepository;
import com.lsg.mingler.domain.product.dao.ProductOptionRepository;
import com.lsg.mingler.domain.product.dao.ProductRepository;
import com.lsg.mingler.domain.product.dto.ProductCard;
import com.lsg.mingler.domain.product.dto.ProductDetail;
import com.lsg.mingler.domain.product.entity.Product;
import com.lsg.mingler.domain.product.entity.ProductImage;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductOptionRepository productOptionRepository;

    /** 메인화면 인기상품 카드 목록: 판매중 상품 판매량순 3개 */
    @Transactional(readOnly = true)
    public List<ProductCard> getPopularProducts() {
        return toCards(productRepository.findTop3ByStatusOrderBySalesCountDescIdDesc(Product.STATUS_ON_SALE));
    }

    /** 메인화면 신상품 카드 목록: 판매중 상품 최신순 3개 */
    @Transactional(readOnly = true)
    public List<ProductCard> getNewProducts() {
        return toCards(productRepository.findTop3ByStatusOrderByCreatedAtDescIdDesc(Product.STATUS_ON_SALE));
    }

    /** 카테고리 목록 페이지: 해당 카테고리들의 판매중 상품 최신순 전체 */
    @Transactional(readOnly = true)
    public List<ProductCard> getCategoryProducts(List<Long> categoryIds) {
        return toCards(productRepository.findByCategoryIdInAndStatusOrderByCreatedAtDescIdDesc(
                categoryIds, Product.STATUS_ON_SALE));
    }

    /** 상품 상세 페이지: 숨김 상품은 404, 조회수 1 증가, 이미지·활성 옵션 포함 */
    @Transactional
    public ProductDetail getProductDetail(Long id) {
        int updatedRows = productRepository.increaseViewCount(id, Product.STATUS_HIDDEN);
        if (updatedRows == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다.");
        }

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다."));

        List<String> imageUrls = productImageRepository.findAllByProductIdOrderByDisplayOrderAscIdAsc(id).stream()
                .map(ProductImage::getImageUrl)
                .toList();
        if (imageUrls.isEmpty() && product.getThumbnailUrl() != null) {
            imageUrls = List.of(product.getThumbnailUrl());
        }

        List<ProductDetail.Option> options =
                productOptionRepository.findAllByProductIdAndIsActiveTrueOrderByDisplayOrderAscIdAsc(id).stream()
                        .map(option -> new ProductDetail.Option(option.getId(), option.getName(),
                                option.getExtraPrice(), option.getStockQuantity()))
                        .toList();

        return new ProductDetail(product.getId(), product.getCategoryId(), product.getName(),
                product.getDescription(), product.getPrice(), product.getSalePrice(),
                product.getStockQuantity(), !product.isOnSale(), imageUrls, options);
    }

    private List<ProductCard> toCards(List<Product> products) {
        return products.stream()
                .map(product -> new ProductCard(
                        product.getId(),
                        product.getName(),
                        product.getPrice(),
                        product.getSalePrice(),
                        product.getThumbnailUrl()))
                .toList();
    }

}
