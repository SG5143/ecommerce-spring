package com.lsg.mingler.domain.product.dao;

import com.lsg.mingler.domain.product.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {
}
