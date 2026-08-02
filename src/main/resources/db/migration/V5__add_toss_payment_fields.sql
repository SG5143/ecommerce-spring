ALTER TABLE `payment`
    ADD COLUMN `pg_order_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT 'PG 결제 시도별 주문번호' AFTER `pg_provider`,
    ADD COLUMN `pg_payment_key` varchar(200) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT 'PG 결제 승인·조회·취소 키' AFTER `pg_order_id`,
    ADD COLUMN `processing_at` datetime DEFAULT NULL COMMENT '승인 처리 시작 시각' AFTER `status`,
    ADD UNIQUE KEY `uk_payment_pg_order_id` (`pg_order_id`),
    ADD UNIQUE KEY `uk_payment_pg_payment_key` (`pg_payment_key`),
    ADD KEY `idx_payment_status_processing` (`status`, `processing_at`),
    ADD KEY `idx_payment_order_status_created` (`order_id`, `status`, `created_at`);

ALTER TABLE `payment`
    DROP CHECK `chk_payment_status`,
    ADD CONSTRAINT `chk_payment_status`
        CHECK (`status` in ('PENDING','PROCESSING','SUCCESS','FAILED','CANCELLED','REFUND_PENDING','REFUNDED','REFUND_FAILED'));
