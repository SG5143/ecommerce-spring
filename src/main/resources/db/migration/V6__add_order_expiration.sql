ALTER TABLE `orders`
    ADD COLUMN `expired_at` datetime DEFAULT NULL COMMENT '결제기한 만료 시각' AFTER `returned_at`;

ALTER TABLE `orders`
    DROP CHECK `chk_orders_status`,
    ADD CONSTRAINT `chk_orders_status`
        CHECK (`status` in ('PENDING_PAYMENT','PAID','PREPARING','SHIPPING','DELIVERED','CANCELLED','RETURN_REQUESTED','RETURNED','EXPIRED'));
