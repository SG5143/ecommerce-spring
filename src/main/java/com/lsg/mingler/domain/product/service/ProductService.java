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

    /** 메인화면 상품 카드 목록: 판매중 상품 최신순 8개 */
    @Transactional(readOnly = true)
    public List<ProductCard> getMainProducts() {
        return productRepository.findTop8ByStatusOrderByCreatedAtDescIdDesc(Product.STATUS_ON_SALE)
                .stream()
                .map(product -> new ProductCard(
                        product.getName(),
                        product.getDisplayPrice(),
                        product.getThumbnailUrl()))
                .toList();
    }

}
