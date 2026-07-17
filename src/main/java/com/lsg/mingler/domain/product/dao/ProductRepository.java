package com.lsg.mingler.domain.product.dao;

import com.lsg.mingler.domain.product.entity.Product;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /** 메인화면: 판매중 상품 최신순 상위 8개 (등록시각 동률 시 id 내림차순) */
    List<Product> findTop8ByStatusOrderByCreatedAtDescIdDesc(String status);

}
