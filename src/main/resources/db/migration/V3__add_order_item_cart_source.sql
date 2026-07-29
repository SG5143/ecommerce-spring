ALTER TABLE `order_item`
    ADD COLUMN `source_cart_item_id` bigint unsigned DEFAULT NULL COMMENT '주문 생성 시 원본 장바구니 상품 행';
