ALTER TABLE `product_option`
    ADD COLUMN `is_default` tinyint(1) NOT NULL DEFAULT '0' COMMENT '무옵션 상품의 숨김 기본 구성 여부' AFTER `is_active`;

CREATE UNIQUE INDEX `uk_product_option_default`
    ON `product_option` ((IF(`is_default`, `product_id`, NULL)));

INSERT INTO `product_option` (
    `product_id`, `name`, `extra_price`, `stock_quantity`, `display_order`, `is_active`, `is_default`, `created_at`, `updated_at`
)
SELECT p.`id`, '기본 구성', 0, p.`stock_quantity`, 0, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM `product` p
WHERE NOT EXISTS (
    SELECT 1
    FROM `product_option` o
    WHERE o.`product_id` = p.`id`
);

UPDATE `cart_item` c
JOIN `product_option` o
    ON o.`product_id` = c.`product_id` AND o.`is_default` = 1
SET c.`product_option_id` = o.`id`
WHERE c.`product_option_id` IS NULL;

UPDATE `order_item` oi
JOIN `orders` o ON o.`id` = oi.`order_id` AND o.`status` = 'PENDING_PAYMENT'
JOIN `product_option` po
    ON po.`product_id` = oi.`product_id` AND po.`is_default` = 1
SET oi.`product_option_id` = po.`id`
WHERE oi.`product_option_id` IS NULL;

ALTER TABLE `cart_item`
    DROP INDEX `uk_cart_item_variant`,
    DROP COLUMN `option_identity`,
    MODIFY COLUMN `product_option_id` bigint unsigned NOT NULL COMMENT '상품 옵션',
    ADD UNIQUE KEY `uk_cart_item_variant` (`cart_id`, `product_id`, `product_option_id`);

ALTER TABLE `product`
    DROP COLUMN `stock_quantity`;
