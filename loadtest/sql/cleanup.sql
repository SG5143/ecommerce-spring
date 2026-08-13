-- 필수 세션 변수:
--   @target_option_id : 경합시킨 product_option.id
--   @run_marker       : LOCUST_DAY12_<LOCK_MODE>_<RUN_ID>
-- 선택 세션 변수:
--   @restore_stock    : 테스트 전 재고. NULL이면 재고를 변경하지 않는다.

START TRANSACTION;

DELETE p
FROM payment p
JOIN orders o ON o.id = p.order_id
WHERE o.orderer_name = CONVERT(@run_marker USING utf8mb4) COLLATE utf8mb4_unicode_ci;

DELETE FROM orders
WHERE orderer_name = CONVERT(@run_marker USING utf8mb4) COLLATE utf8mb4_unicode_ci;

UPDATE product_option
SET stock_quantity = @restore_stock
WHERE id = @target_option_id
  AND @restore_stock IS NOT NULL;

COMMIT;

SELECT id, product_id, stock_quantity
FROM product_option
WHERE id = @target_option_id;
