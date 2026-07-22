-- mingler.orders definition

CREATE TABLE `orders`
(
    `id`                    bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '고유식별자',
    `order_number`          varchar(50) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '외부 노출 주문번호',
    `member_id`             bigint unsigned DEFAULT NULL COMMENT '주문 회원, 비회원은 NULL',
    `guest_token_hash`      char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '비회원 주문 조회 토큰 SHA-256',
    `orderer_name`          varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '주문자명 스냅샷',
    `orderer_phone`         varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '주문자 연락처 스냅샷',
    `orderer_email`         varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '주문자 이메일 스냅샷',
    `receiver_name`         varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '수령인명',
    `receiver_phone`        varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '수령인 연락처',
    `zipcode`               varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '배송지 우편번호',
    `address`               varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '배송지 기본주소',
    `address_detail`        varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '배송지 상세주소',
    `delivery_message`      varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '배송 요청사항',
    `merchandise_amount`    int unsigned NOT NULL COMMENT '상품금액 합계',
    `discount_amount`       int unsigned NOT NULL DEFAULT '0' COMMENT '할인금액 합계',
    `shipping_fee`          int unsigned NOT NULL DEFAULT '0' COMMENT '배송비',
    `total_amount`          int unsigned NOT NULL COMMENT '최종 결제금액',
    `status`                varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING_PAYMENT' COMMENT '주문상태',
    `paid_at`               datetime DEFAULT NULL COMMENT '결제완료 시각',
    `cancelled_at`          datetime DEFAULT NULL COMMENT '주문취소 시각',
    `shipping_started_at`   datetime DEFAULT NULL COMMENT '배송시작 시각',
    `delivered_at`          datetime DEFAULT NULL COMMENT '배송완료 시각',
    `return_requested_at`   datetime DEFAULT NULL COMMENT '반품요청 시각',
    `returned_at`           datetime DEFAULT NULL COMMENT '반품완료 시각',
    `created_at`            datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '주문생성 시각',
    `updated_at`            datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_orders_order_number` (`order_number`),
    UNIQUE KEY `uk_orders_guest_token_hash` (`guest_token_hash`),
    KEY `idx_orders_member_created` (`member_id`,`created_at`),
    KEY `idx_orders_status_created` (`status`,`created_at`),
    CONSTRAINT `fk_orders_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`),
    CONSTRAINT `chk_orders_owner` CHECK (((`member_id` is not null) and (`guest_token_hash` is null)) or
                                          ((`member_id` is null) and (`guest_token_hash` is not null))),
    CONSTRAINT `chk_orders_amount` CHECK (`total_amount` = (`merchandise_amount` - `discount_amount` + `shipping_fee`)),
    CONSTRAINT `chk_orders_discount` CHECK (`discount_amount` <= `merchandise_amount`),
    CONSTRAINT `chk_orders_status` CHECK (`status` in ('PENDING_PAYMENT','PAID','PREPARING','SHIPPING','DELIVERED','CANCELLED','RETURN_REQUESTED','RETURNED'))
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='주문';


-- mingler.order_item definition

CREATE TABLE `order_item`
(
    `id`                 bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '고유식별자',
    `order_id`           bigint unsigned NOT NULL COMMENT '주문',
    `product_id`         bigint unsigned NOT NULL COMMENT '원본 상품',
    `product_option_id`  bigint unsigned DEFAULT NULL COMMENT '원본 상품 옵션, 단일 구성은 NULL',
    `product_name`       varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '주문 당시 상품명',
    `category_id`        bigint unsigned NOT NULL COMMENT '주문 당시 카테고리 식별자',
    `category_name`      varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '주문 당시 카테고리명',
    `option_name`        varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '주문 당시 옵션명',
    `thumbnail_url`      varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '주문 당시 대표 이미지 URL',
    `unit_price`         int unsigned NOT NULL COMMENT '주문 당시 옵션 포함 단가',
    `quantity`           int unsigned NOT NULL COMMENT '주문수량',
    `line_amount`        int unsigned NOT NULL COMMENT '항목 합계금액',
    `created_at`         datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    PRIMARY KEY (`id`),
    KEY `idx_order_item_order` (`order_id`),
    KEY `idx_order_item_product` (`product_id`),
    KEY `idx_order_item_option_product` (`product_option_id`,`product_id`),
    CONSTRAINT `fk_order_item_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_order_item_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`),
    CONSTRAINT `fk_order_item_option_product` FOREIGN KEY (`product_option_id`, `product_id`) REFERENCES `product_option` (`id`, `product_id`),
    CONSTRAINT `chk_order_item_quantity` CHECK (`quantity` between 1 and 99),
    CONSTRAINT `chk_order_item_amount` CHECK (`line_amount` = (`unit_price` * `quantity`))
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='주문 상품 스냅샷';


-- mingler.payment definition

CREATE TABLE `payment`
(
    `id`                   bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '고유식별자',
    `payment_number`       varchar(50) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '외부 노출 결제번호',
    `order_id`             bigint unsigned NOT NULL COMMENT '결제 대상 주문',
    `member_id`            bigint unsigned DEFAULT NULL COMMENT '결제 회원, 비회원은 NULL',
    `idempotency_key`      varchar(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '결제 승인 요청 멱등성 키',
    `pg_provider`          varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'PG 제공사',
    `payment_method`       varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '결제수단',
    `amount`               int unsigned NOT NULL COMMENT '결제 승인 요청금액',
    `status`               varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT '결제상태',
    `pg_transaction_key`   varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'PG 거래 식별키',
    `failure_code`         varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '실패 코드',
    `failure_reason`       varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '실패 사유',
    `approved_at`          datetime DEFAULT NULL COMMENT '승인완료 시각',
    `failed_at`            datetime DEFAULT NULL COMMENT '승인실패 시각',
    `cancelled_at`         datetime DEFAULT NULL COMMENT '승인 전 취소 시각',
    `refund_requested_at`  datetime DEFAULT NULL COMMENT '환불요청 시각',
    `refunded_at`          datetime DEFAULT NULL COMMENT '환불완료 시각',
    `refund_failed_at`     datetime DEFAULT NULL COMMENT '환불실패 시각',
    `created_at`           datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '결제요청 시각',
    `updated_at`           datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_payment_payment_number` (`payment_number`),
    UNIQUE KEY `uk_payment_member_idempotency` (`member_id`,`idempotency_key`),
    UNIQUE KEY `uk_payment_order_idempotency` (`order_id`,`idempotency_key`),
    UNIQUE KEY `uk_payment_pg_transaction_key` (`pg_transaction_key`),
    KEY `idx_payment_order_created` (`order_id`,`created_at`),
    KEY `idx_payment_status_created` (`status`,`created_at`),
    CONSTRAINT `fk_payment_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`),
    CONSTRAINT `fk_payment_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`),
    CONSTRAINT `chk_payment_amount` CHECK (`amount` > 0),
    CONSTRAINT `chk_payment_status` CHECK (`status` in ('PENDING','SUCCESS','FAILED','CANCELLED','REFUND_PENDING','REFUNDED','REFUND_FAILED'))
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='결제 시도';
