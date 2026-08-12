-- 필수 세션 변수:
--   @target_option_id : 경합시킬 product_option.id
--   @initial_stock    : 테스트 시작 재고
--   @run_marker       : LOCUST_DAY12_<LOCK_MODE>_<RUN_ID>

SELECT
    o.id AS option_id,
    o.product_id,
    o.stock_quantity AS stock_before_test,
    @initial_stock AS stock_for_test,
    @run_marker AS run_marker
FROM product_option o
WHERE o.id = @target_option_id;

START TRANSACTION;

DELETE p
FROM payment p
JOIN orders o ON o.id = p.order_id
WHERE o.orderer_name = CONVERT(@run_marker USING utf8mb4) COLLATE utf8mb4_unicode_ci;

DELETE FROM orders
WHERE orderer_name = CONVERT(@run_marker USING utf8mb4) COLLATE utf8mb4_unicode_ci;

UPDATE product_option
SET stock_quantity = @initial_stock
WHERE id = @target_option_id;

COMMIT;

SELECT id, product_id, stock_quantity
FROM product_option
WHERE id = @target_option_id;
