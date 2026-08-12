-- 필수 세션 변수:
--   @target_option_id : 경합시킨 product_option.id
--   @initial_stock    : 테스트 시작 재고
--   @run_marker       : LOCUST_DAY12_<LOCK_MODE>_<RUN_ID>

SELECT
    @run_marker AS run_marker,
    @initial_stock AS initial_stock,
    po.stock_quantity AS final_stock,
    COUNT(DISTINCT o.id) AS created_orders,
    COUNT(DISTINCT CASE WHEN p.status = 'SUCCESS' THEN p.id END) AS successful_payments,
    COUNT(DISTINCT CASE WHEN p.status = 'PENDING' THEN p.id END) AS stock_rejected_payments,
    (
        COUNT(DISTINCT CASE WHEN p.status = 'SUCCESS' THEN p.id END) + po.stock_quantity
        = @initial_stock
    ) AS stock_conservation_passed,
    (
        COUNT(DISTINCT CASE WHEN p.status = 'SUCCESS' THEN p.id END)
        <= @initial_stock
    ) AS no_oversell_passed
FROM product_option po
LEFT JOIN orders o
    ON o.orderer_name = CONVERT(@run_marker USING utf8mb4) COLLATE utf8mb4_unicode_ci
LEFT JOIN payment p ON p.order_id = o.id
WHERE po.id = @target_option_id
GROUP BY po.id, po.stock_quantity;
