package com.lsg.mingler.domain.product.service;

import com.lsg.mingler.domain.product.dao.ProductRepository;
import com.lsg.mingler.domain.product.dto.ProductCard;
import com.lsg.mingler.domain.product.entity.Product;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

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

    private List<ProductCard> toCards(List<Product> products) {
        return products.stream()
                .map(product -> new ProductCard(
                        product.getName(),
                        product.getPrice(),
                        product.getSalePrice(),
                        product.getThumbnailUrl()))
                .toList();
    }

}
