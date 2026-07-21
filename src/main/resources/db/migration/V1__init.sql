-- mingler.`member` definition

CREATE TABLE `member`
(
    `id`                   bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '고유식별자',
    `username`             varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '아이디',
    `password`             varchar(255) COLLATE utf8mb4_unicode_ci                      NOT NULL COMMENT '비밀번호',
    `name`                 varchar(50) COLLATE utf8mb4_unicode_ci                       NOT NULL COMMENT '실명',
    `phone`                varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '휴대폰',
    `phone_verified_at`    datetime                                                              DEFAULT NULL COMMENT '휴대폰 본인확인 완료일시 (미인증 시 NULL)',
    `email`                varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci         DEFAULT NULL COMMENT '이메일',
    `email_agreed`         tinyint(1) NOT NULL DEFAULT '0' COMMENT 'SNS(이메일) 수신 동의 여부',
    `email_agreed_at`      datetime                                                              DEFAULT NULL COMMENT '이메일 수신 동의 시각',
    `sms_agreed`           tinyint(1) NOT NULL DEFAULT '0' COMMENT 'SMS 수신 동의 여부',
    `sms_agreed_at`        datetime                                                              DEFAULT NULL COMMENT 'SMS 수신 동의 시각',
    `birth_date`           date                                                         NOT NULL COMMENT '생년월일',
    `provider`             varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'LOCAL' COMMENT '가입경로',
    `provider_id`          varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci         DEFAULT NULL COMMENT '소셜 로그인 고유 사용자 ID',
    `role`                 varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'USER' COMMENT '권한구분',
    `status`               varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '회원상태',
    `grade`                varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'BRONZE' COMMENT '등급',
    `point_balance`        int unsigned NOT NULL DEFAULT '0' COMMENT '보유적립금',
    `marketing_agreed`     tinyint(1) NOT NULL DEFAULT '0' COMMENT '마케팅 정보 수신 동의 여부',
    `marketing_agreed_at`  datetime                                                              DEFAULT NULL COMMENT '마케팅 수신 동의 시각',
    `terms_agreed_at`      datetime                                                     NOT NULL COMMENT '이용약관 동의 시각',
    `privacy_agreed_at`    datetime                                                     NOT NULL COMMENT '개인정보 처리방침 동의 시각',
    `login_fail_count`     int unsigned NOT NULL DEFAULT '0' COMMENT '연속 로그인 실패횟수',
    `account_locked_until` datetime                                                              DEFAULT NULL COMMENT '잠금 해제일시',
    `last_login_at`        datetime                                                              DEFAULT NULL COMMENT '최근 로그인 일시',
    `withdrawn_at`         datetime                                                              DEFAULT NULL COMMENT '탈퇴시각',
    `created_at`           datetime                                                     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '가입일시',
    `updated_at`           datetime                                                     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_member_username` (`username`),
    UNIQUE KEY `uk_member_phone` (`phone`),
    UNIQUE KEY `uk_member_provider_provider_id` (`provider`,`provider_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='회원 정보';


-- mingler.cart definition

CREATE TABLE `cart`
(
    `id`               bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '고유식별자',
    `member_id`        bigint unsigned DEFAULT NULL COMMENT '회원 장바구니 소유자',
    `guest_token_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '비회원 식별 토큰 SHA-\n      256',
    `expires_at`       datetime                                       DEFAULT NULL COMMENT '비회원 장바구니 만료시각',
    `created_at`       datetime NOT NULL                              DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    `updated_at`       datetime NOT NULL                              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cart_member` (`member_id`),
    UNIQUE KEY `uk_cart_guest_token_hash` (`guest_token_hash`),
    KEY                `idx_cart_expires_at` (`expires_at`),
    CONSTRAINT `fk_cart_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`) ON DELETE CASCADE,
    CONSTRAINT `chk_cart_owner` CHECK ((
        ((`member_id` is not null) and (`guest_token_hash` is null) and (`expires_at` is null)) or
        ((`member_id` is null) and (`guest_token_hash` is not null) and (`expires_at` is not null))))
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='회원·비회원 장바구니';


-- mingler.category definition

CREATE TABLE `category`
(
    `id`            bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '고유식별자',
    `parent_id`     bigint unsigned DEFAULT NULL COMMENT '상위 카테고리',
    `name`          varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '카테고리명',
    `display_order` int unsigned NOT NULL DEFAULT '0' COMMENT '노출순서',
    `is_active`     tinyint(1) NOT NULL DEFAULT '1' COMMENT '노출여부',
    `created_at`    datetime                                                     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시',
    `updated_at`    datetime                                                     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_category_parent_name` (`parent_id`,`name`),
    CONSTRAINT `fk_category_parent` FOREIGN KEY (`parent_id`) REFERENCES `category` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='상품 카테고리 (계층 구조)';


-- mingler.member_address definition

CREATE TABLE `member_address`
(
    `id`             bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '고유식별자',
    `member_id`      bigint unsigned NOT NULL COMMENT '소유회원',
    `alias`          varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci           DEFAULT NULL COMMENT '배송지별칭',
    `receiver_name`  varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  NOT NULL COMMENT '수령인명',
    `receiver_phone` varchar(20) COLLATE utf8mb4_unicode_ci                        NOT NULL COMMENT '수령인 연락처',
    `zipcode`        varchar(10) COLLATE utf8mb4_unicode_ci                        NOT NULL COMMENT '우편번호',
    `address`        varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '기본주소',
    `address_detail` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci          DEFAULT NULL COMMENT '상세주소',
    `is_default`     tinyint(1) NOT NULL DEFAULT '0' COMMENT '기본 배송지 여부',
    `created_at`     datetime                                                      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시',
    `updated_at`     datetime                                                      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    PRIMARY KEY (`id`),
    KEY              `idx_member_address_member_id` (`member_id`),
    CONSTRAINT `fk_member_address_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='회원 배송지 정보';


-- mingler.product definition

CREATE TABLE `product`
(
    `id`             bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '고유식별자',
    `category_id`    bigint unsigned NOT NULL COMMENT '소속 카테고리',
    `name`           varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '상품명',
    `description`    text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '상품 상세설명',
    `price`          int unsigned NOT NULL COMMENT '정가',
    `sale_price`     int unsigned DEFAULT NULL COMMENT '할인가 (NULL이면 할인 없음)',
    `stock_quantity` int unsigned NOT NULL DEFAULT '0' COMMENT '재고 수량',
    `status`         varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  NOT NULL DEFAULT 'ON_SALE' COMMENT '판매상태 (ON_SALE/SOLD_OUT/HIDDEN)',
    `thumbnail_url`  varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci          DEFAULT NULL COMMENT '대표 이미지 URL (product_image display_order=0 비정규화)',
    `view_count`     int unsigned NOT NULL DEFAULT '0' COMMENT '조회수',
    `sales_count`    int unsigned NOT NULL DEFAULT '0' COMMENT '누적 판매량',
    `created_at`     datetime                                                      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시',
    `updated_at`     datetime                                                      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    PRIMARY KEY (`id`),
    KEY              `idx_product_category` (`category_id`),
    KEY              `idx_product_status_created` (`status`,`created_at`),
    KEY              `idx_product_status_sales` (`status`,`sales_count`),
    CONSTRAINT `fk_product_category` FOREIGN KEY (`category_id`) REFERENCES `category` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='상품';


-- mingler.product_image definition

CREATE TABLE `product_image`
(
    `id`            bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '고유식별자',
    `product_id`    bigint unsigned NOT NULL COMMENT '소속 상품',
    `image_url`     varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '이미지 URL',
    `display_order` int unsigned NOT NULL DEFAULT '0' COMMENT '노출 순서 (0이 대표 이미지)',
    `created_at`    datetime                                                      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_product_image_order` (`product_id`,`display_order`),
    CONSTRAINT `fk_product_image_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='상품 이미지 (상품당 다중)';


-- mingler.product_option definition

CREATE TABLE `product_option`
(
    `id`             bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '고유식별자',
    `product_id`     bigint unsigned NOT NULL COMMENT '소속 상품',
    `name`           varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '옵션명',
    `extra_price`    int unsigned NOT NULL DEFAULT '0' COMMENT '옵션 추가금액 (0이면 추가금 없음)',
    `stock_quantity` int unsigned NOT NULL DEFAULT '0' COMMENT '옵션 재고 수량 (0이면 품절)',
    `display_order`  int unsigned NOT NULL DEFAULT '0' COMMENT '노출 순서',
    `is_active`      tinyint(1) NOT NULL DEFAULT '1' COMMENT '노출여부',
    `created_at`     datetime                                                      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시',
    `updated_at`     datetime                                                      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_product_option_name` (`product_id`,`name`),
    UNIQUE KEY `uk_product_option_id_product` (`id`,`product_id`),
    CONSTRAINT `fk_product_option_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='상품 옵션 (상품당 다중, 없으면 단일 구성 상품)';


-- mingler.refresh_token definition

CREATE TABLE `refresh_token`
(
    `id`         bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '고유식별자',
    `member_id`  bigint unsigned NOT NULL COMMENT '소유회원',
    `token_hash` char(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Refresh 토큰 원문의 SHA-256 hex',
    `expires_at` datetime                            NOT NULL COMMENT '만료일시',
    `revoked_at` datetime                                     DEFAULT NULL COMMENT '폐기일시',
    `created_at` datetime                            NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '발급일시',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_refresh_token_hash` (`token_hash`),
    KEY          `idx_refresh_token_member` (`member_id`),
    CONSTRAINT `fk_refresh_token_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Refresh 토큰 (회전형, 재사용 탐지)';


-- mingler.cart_item definition

CREATE TABLE `cart_item`
(
    `id`                  bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '고유식별자',
    `cart_id`             bigint unsigned NOT NULL COMMENT '장바구니',
    `product_id`          bigint unsigned NOT NULL COMMENT '상품',
    `product_option_id`   bigint unsigned DEFAULT NULL COMMENT '상품 옵션, 단일 구성은 NULL',
    `option_identity`     bigint unsigned GENERATED ALWAYS AS (ifnull(`product_option_id`,0)) STORED COMMENT 'NULL 옵션 중복 방지용 식별자',
    `quantity`            int      NOT NULL,
    `unit_price_at_added` int unsigned NOT NULL COMMENT '처음 담을 당시 옵션 포함 단가',
    `created_at`          datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '담은 일시',
    `updated_at`          datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일\n      시',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cart_item_variant` (`cart_id`,`product_id`,`option_identity`),
    KEY                   `idx_cart_item_product` (`product_id`),
    KEY                   `idx_cart_item_option_product` (`product_option_id`,`product_id`),
    CONSTRAINT `fk_cart_item_cart` FOREIGN KEY (`cart_id`) REFERENCES `cart` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_cart_item_option_product` FOREIGN KEY (`product_option_id`, `product_id`) REFERENCES `product_option` (`id`, `product_id`),
    CONSTRAINT `fk_cart_item_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`),
    CONSTRAINT `chk_cart_item_quantity` CHECK ((`quantity` between 1 and 99))
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='장바구니 상품';

INSERT INTO category (parent_id, name, display_order, is_active, created_at, updated_at)
VALUES (NULL, 'Apparel', 1, 1, '2026-07-17 10:27:11', '2026-07-19 06:56:40'),
       (NULL, 'Accessories', 2, 1, '2026-07-17 10:27:11', '2026-07-19 06:56:40'),
       (1, 'Tops', 1, 1, '2026-07-17 10:27:14', '2026-07-19 06:56:40'),
       (1, 'Bottoms', 2, 1, '2026-07-17 10:27:14', '2026-07-19 06:56:40'),
       (2, 'Bags', 1, 1, '2026-07-17 10:27:14', '2026-07-19 06:56:40'),
       (2, 'Shoes', 2, 1, '2026-07-17 10:27:14', '2026-07-19 06:56:40');
INSERT INTO product (category_id, name, description, price, sale_price, stock_quantity, status, thumbnail_url,
                             view_count, sales_count, created_at, updated_at)
VALUES (3, '캐시미어 블렌드 라운드넥 니트', '부드러운 캐시미어 혼방 소재의 데일리 니트. 목선이 깔끔한 라운드넥 실루엣.', 89000, 69000, 120, 'ON_SALE',
        'https://images.kolonmall.com/Prod_Img/60004776/2025/LM1/K1759238509127085NO01_LM1.jpg', 1576, 213,
        '2026-07-10 10:27:18', '2026-07-20 13:14:39'),
       (3, '스트라이프 옥스포드 셔츠', '탄탄한 옥스포드 원단에 클래식한 스트라이프 패턴을 더한 셔츠.', 45000, NULL, 85, 'ON_SALE',
        'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcT1qR7h0SjU3Fa528unNtnuuedwoZKCvnbPHGSeGLg_Vw&s=10', 875,
        96, '2026-07-03 10:27:18', '2026-07-20 09:47:16'),
       (3, '베이식 코튼 맨투맨', '순면 100% 기본 맨투맨. 어떤 하의와도 잘 어울리는 무난한 핏.', 32000, 25900, 200, 'ON_SALE',
        'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSY3jDydCeKz31SIBb2AxPYquu_fimOoMJ0Da2N5QsVlw&s', 2310,
        428, '2026-06-26 10:27:18', '2026-07-19 07:15:53'),
       (3, '울 블렌드 오버핏 코트', '겨울 시즌 대표 아우터. 울 혼방 소재로 보온성과 실루엣을 모두 잡은 오버핏 코트.', 158000, NULL, 40, 'ON_SALE',
        'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTH5l5aV5TxFgi2Hu8fSZXSc_DY7arqy2jKAvyUXKhMdg&s=10', 1980,
        154, '2026-06-19 10:27:18', '2026-07-19 07:17:03'),
       (3, '소프트 터틀넥 스웨터', '까슬거림 없는 소프트 아크릴 혼방 터틀넥. 단독으로도 이너로도 활용도 높음.', 54000, 43900, 95, 'ON_SALE',
        'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRn0DCqe4ZU81PQaVQyS6Xxo4MLPrfwRyIqMiaaUliSKQ&s', 655, 71,
        '2026-06-12 10:27:18', '2026-07-20 09:47:40'),
       (3, '린넨 반팔 셔츠', '여름용 시원한 린넨 혼방 반팔 셔츠. 자연스러운 구김이 매력.', 39000, NULL, 0, 'SOLD_OUT',
        'https://placehold.co/300x300?text=Linen+Shirt+1', 1121, 302, '2026-07-11 10:27:18', '2026-07-19 16:52:09'),
       (3, '후드 집업 자켓', '간절기 활용도 높은 기모 안감 후드 집업.', 59000, NULL, 150, 'ON_SALE',
        'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcR1ysc-n5XXzA4LFcKIB9L1cqJ8QDwVTVI2ynREont6KQ&s', 931,
        118, '2026-07-04 10:27:18', '2026-07-20 09:46:59'),
       (3, '램스울 브이넥 가디건', '램스울 혼방의 클래식 브이넥 가디건. 오피스룩에도 잘 어울림.', 72000, 59000, 60, 'ON_SALE',
        'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcS_kdSDfnLJkJ1jbMxZGxAogsbpBls0gTNRrOZGfZlDdw&s=10', 445,
        38, '2026-06-27 10:27:18', '2026-07-19 07:15:27'),
       (3, '헤비웨이트 무지 티셔츠', '두툼한 헤비웨이트 코튼으로 형태 유지력이 좋은 무지 티셔츠.', 19000, NULL, 300, 'ON_SALE',
        'https://ae-pic-a1.aliexpress-media.com/kf/Sd003dec18de54fb08dd454c6f9bb3b40X.jpg_960x960q75.jpg_.avif', 3125,
        892, '2026-06-20 10:27:18', '2026-07-20 09:52:19'),
       (4, '코튼 와이드 밴딩 팬츠', '허리 전체 밴딩으로 편안한 착용감의 코튼 와이드 팬츠.', 49000, NULL, 110, 'ON_SALE',
        'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcS7aANlfA4_qPG-KsOCzgRXQ7Bw1XuKBY692qQYVkQYfA&s', 1287,
        245, '2026-06-13 10:27:18', '2026-07-19 07:18:01');
INSERT INTO product (category_id, name, description, price, sale_price, stock_quantity, status, thumbnail_url,
                             view_count, sales_count, created_at, updated_at)
VALUES (4, '슬림 스트레이트 데님', '허벅지는 여유 있고 밑단은 곧게 떨어지는 슬림 스트레이트 핏 데님.', 55000, 46900, 130, 'ON_SALE',
        'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcS9AhAVMPzhsFLFVkUWl37wjWzrFqoRoWvUNg77V4rcCA&s=10', 1776,
        331, '2026-07-12 10:27:18', '2026-07-20 09:52:17'),
       (4, '울 슬랙스', '겨울용 울 혼방 슬랙스. 세미와이드 핏으로 격식과 편안함을 동시에.', 68000, NULL, 70, 'ON_SALE',
        'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSYuYpiXCtH0yoLw979XHALlf42H1oXN2Pf3g-aicVKtQ&s=10', 542,
        49, '2026-07-05 10:27:18', '2026-07-19 07:12:48'),
       (4, '카고 조거 팬츠', '입체 포켓 디테일의 스트리트 무드 카고 조거.', 43000, NULL, 160, 'ON_SALE',
        'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSWHnJxzIeKF3vhV_aZYothMOMVB0cvclZ8uTXLHgO_pQ&s=10', 998,
        187, '2026-06-28 10:27:18', '2026-07-19 07:15:04'),
       (4, '하이웨이스트 플리츠 스커트', '잔잔한 플리츠가 돋보이는 하이웨이스트 미디 스커트.', 38000, 29900, 90, 'ON_SALE',
        'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSUSqQZWF2r6mydndrWFvPexX526cEXZ-G-Xj7oCvgImQ&s', 1432,
        276, '2026-06-21 10:27:18', '2026-07-19 07:16:39'),
       (4, '스트레치 치노 팬츠', '신축성 좋은 스트레치 치노. 출근룩 기본템.', 41000, NULL, 140, 'ON_SALE',
        'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSf-r00u2kONOijG4OKdlnAoqz7D6_0pPAoKTx2C6EuBA&s=10', 765,
        132, '2026-06-14 10:27:18', '2026-07-19 07:17:28'),
       (4, '와이드 데님 팬츠', '과감하게 떨어지는 와이드 실루엣 데님.', 57000, NULL, 0, 'SOLD_OUT',
        'https://placehold.co/300x300?text=Wide+Denim+1', 2087, 415, '2026-07-13 10:27:18', '2026-07-17 10:27:18'),
       (4, '벨티드 버뮤다 쇼츠', '동일 원단 벨트가 세트인 버뮤다 기장 쇼츠.', 33000, NULL, 75, 'ON_SALE',
        'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSmSjIpiNDuceAtJS8v96DRdpPsxntANLuj6GZYBq1S1A&s=10', 390,
        44, '2026-07-06 10:27:18', '2026-07-20 10:26:48'),
       (4, '코듀로이 테이퍼드 팬츠', '가을·겨울 시즌 코듀로이 소재의 테이퍼드 핏 팬츠.', 47000, 39900, 105, 'ON_SALE',
        'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQVffFIXdggYGua2CbATOOCoy7ZpTOevgfOifyvALeEEQ&s=10', 624,
        87, '2026-06-29 10:27:18', '2026-07-19 16:54:36'),
       (5, '미니멀 레더 크로스백', '군더더기 없는 디자인의 소가죽 크로스백. 데일리로 부담 없는 사이즈.', 72000, 59900, 55, 'ON_SALE',
        'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSagZZ1eImQJWR9gK7KVN9Y07ASJYb-POddkg7q7IY6_w&s=10', 1876,
        298, '2026-06-22 10:27:18', '2026-07-19 07:20:24'),
       (5, '데일리 캔버스 백팩', '노트북 수납이 가능한 넉넉한 용량의 캔버스 백팩.', 65000, NULL, 80, 'ON_SALE',
        'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRy2BObnqfs2WuDbRtAJQ61qm03_XFnqbLW1DZlNezUlw&s', 1352,
        221, '2026-06-15 10:27:18', '2026-07-19 16:58:36');
INSERT INTO product (category_id, name, description, price, sale_price, stock_quantity, status, thumbnail_url,
                             view_count, sales_count, created_at, updated_at)
VALUES (5, '클래식 브리프케이스', '비즈니스 룩을 완성하는 클래식 무드의 브리프케이스.', 128000, NULL, 30, 'ON_SALE',
        'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcR_Fg3RQFcxWSUGBBc-yP4Xki689tBYFyIBTgPtytYTmA&s=10', 457,
        27, '2026-07-14 10:27:18', '2026-07-19 16:49:42'),
       (5, '퀼팅 체인 숄더백', '볼륨감 있는 퀼팅과 골드 체인 스트랩의 숄더백.', 98000, 79000, 45, 'ON_SALE',
        'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRL4PRH6l2YlJMgg7PpFxioegplQ4ed0bYDDeY88mHx1w&s=10', 2236,
        187, '2026-07-07 10:27:18', '2026-07-20 10:31:29'),
       (5, '에코 캔버스 토트백', '가볍게 들기 좋은 코튼 캔버스 에코 토트백.', 24000, NULL, 250, 'ON_SALE',
        'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSzI8zQbFLoWJCn7JXEyKUiDeZe8G-Xx2EjxVT425s5Kg&s', 1680,
        534, '2026-06-30 10:27:18', '2026-07-20 12:11:53'),
       (5, '나일론 슬링백', '가벼운 나일론 소재의 캐주얼 슬링백. 여행용 보조가방으로도 좋음.', 36000, NULL, 120, 'ON_SALE',
        'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTJd4guvPg-hqmMzaZt6ZdizVMl-D6JQh_0AN-WX8qiSA&s=10', 843,
        156, '2026-06-23 10:27:18', '2026-07-19 07:19:59'),
       (5, '소프트 레더 클러치', '부드러운 가죽 질감의 미니멀 클러치.', 58000, NULL, 35, 'ON_SALE',
        'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQk52u33HVXDFx8oH2llr2Yb7aYQAewI1mozlcFtP7gbw&s=10', 312,
        19, '2026-06-16 10:27:18', '2026-07-19 07:20:52'),
       (5, '트래블 더플백', '1박 2일 여행에 알맞은 사이즈의 더플백. 숄더 스트랩 포함.', 89000, 71000, 50, 'ON_SALE',
        'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQ8b-wgeTmZXsFnI0_oBjYePa0Azfj1m-mWOgrmC9eXlg&s=10', 731,
        63, '2026-07-15 10:27:18', '2026-07-20 10:28:00'),
       (5, '미니 버킷백', '트렌디한 미니 사이즈 버킷백. 스트랩 길이 조절 가능.', 62000, NULL, 0, 'SOLD_OUT',
        'https://placehold.co/300x300?text=Bucketbag+1', 1954, 342, '2026-07-08 10:27:18', '2026-07-17 10:27:18'),
       (6, '클래식 코트 스니커즈', '어디에나 어울리는 화이트 베이스의 클래식 코트 스니커즈.', 79000, NULL, 180, 'ON_SALE',
        'https://m.chaakan.co.kr/web/product/extra/small/202407/3f2d10c6ee68ae977d16df7ed9d8e583.jpg', 2765, 612,
        '2026-07-01 10:27:18', '2026-07-18 14:39:11'),
       (6, '스웨이드 첼시 부츠', '부드러운 스웨이드 소재의 사이드 고어 첼시 부츠.', 138000, 109000, 40, 'ON_SALE',
        'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTQ5nSkOLrkKlGfemXNhFngcpVgdnXEi_byG07pFICG4Q&s=10', 1123,
        98, '2026-06-24 10:27:18', '2026-07-19 07:09:24'),
       (6, '러닝 메시 스니커즈', '통기성 좋은 메시 어퍼와 쿠셔닝 밑창의 러닝화.', 88000, NULL, 145, 'ON_SALE',
        'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQuOFaBwbjmPA_SGS7JylH7pvk55bznwksA8RuIc-wHUQ&s=10', 1567,
        234, '2026-06-17 10:27:18', '2026-07-19 07:10:13');
INSERT INTO product (category_id, name, description, price, sale_price, stock_quantity, status, thumbnail_url,
                             view_count, sales_count, created_at, updated_at)
VALUES (6, '레더 로퍼', '정장과 캐주얼을 넘나드는 클래식 페니 로퍼.', 115000, NULL, 55, 'ON_SALE',
        'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRA5O4BgWL-aggZiC9ClGH7jNcV5QSHVJaasuuVxyVqbQ&s=10', 878,
        67, '2026-07-16 10:27:18', '2026-07-20 13:14:24'),
       (6, '캔버스 하이탑 스니커즈', '빈티지 무드의 캔버스 하이탑. 발목까지 감싸는 안정감.', 49000, 39900, 170, 'ON_SALE',
        'https://img.danuri.io/catalog-image/435/425/000/9af5516c0bd646249b1a65b93169959b.jpg?shrink=500:*&_v=20260719101058',
        1998, 445, '2026-07-09 10:27:18', '2026-07-19 07:06:36'),
       (6, '첼시 레인부츠', '비 오는 날에도 스타일리시한 첼시 스타일 레인부츠.', 45000, NULL, 65, 'ON_SALE',
        'https://img.ssfshop.com/cmd/LB_750x1000/src/https://img.ssfshop.com/goods/REFA/26/05/19/GPMY26051905484_0_ORGINL_20260601174409054.jpg',
        534, 72, '2026-07-02 10:27:18', '2026-07-19 07:07:27'),
       (6, '소프트 레더 샌들', '푹신한 풋베드의 여름용 가죽 샌들.', 52000, NULL, 90, 'ON_SALE',
        'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTvWur1b8omlfByb5iCSxOLWWQe71Y_0yjsoBAxeRyCcA&s=10', 687,
        113, '2026-06-25 10:27:18', '2026-07-19 07:08:19'),
       (6, '클래식 더비 슈즈', '격식 있는 자리에 어울리는 클래식 더비 슈즈.', 129000, 99000, 35, 'ON_SALE',
        'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQWI-tsMArvHxZEbNrB8VVyZoCwzh1WZi5o6sUE8Y_FHg&s', 398, 31,
        '2026-06-18 10:27:18', '2026-07-19 07:09:48'),
       (6, '라이트 슬립온', '끈 없이 편하게 신는 초경량 슬립온.', 42000, NULL, 200, 'ON_SALE',
        'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRutp97L7jzn-DoBSzEdBoXv7R4QZj4W8ATAFzx2VrBdQ&s', 1246,
        289, '2026-07-17 10:27:18', '2026-07-20 10:28:12');
INSERT INTO product_image (product_id, image_url, display_order, created_at)
VALUES (1, 'https://images.kolonmall.com/Prod_Img/60004776/2025/LM1/K1759238509127085NO01_LM1.jpg', 0,
        '2026-07-17 10:27:22'),
       (2, 'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcT1qR7h0SjU3Fa528unNtnuuedwoZKCvnbPHGSeGLg_Vw&s=10', 0,
        '2026-07-17 10:27:22'),
       (3, 'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSY3jDydCeKz31SIBb2AxPYquu_fimOoMJ0Da2N5QsVlw&s', 0,
        '2026-07-17 10:27:22'),
       (4, 'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTH5l5aV5TxFgi2Hu8fSZXSc_DY7arqy2jKAvyUXKhMdg&s=10', 0,
        '2026-07-17 10:27:22'),
       (5, 'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRn0DCqe4ZU81PQaVQyS6Xxo4MLPrfwRyIqMiaaUliSKQ&s', 0,
        '2026-07-17 10:27:22'),
       (6, 'https://placehold.co/300x300?text=Linen+Shirt+1', 0, '2026-07-17 10:27:22'),
       (7, 'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcR1ysc-n5XXzA4LFcKIB9L1cqJ8QDwVTVI2ynREont6KQ&s', 0,
        '2026-07-17 10:27:22'),
       (8, 'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcS_kdSDfnLJkJ1jbMxZGxAogsbpBls0gTNRrOZGfZlDdw&s=10', 0,
        '2026-07-17 10:27:22'),
       (9, 'https://ae-pic-a1.aliexpress-media.com/kf/Sd003dec18de54fb08dd454c6f9bb3b40X.jpg_960x960q75.jpg_.avif', 0,
        '2026-07-17 10:27:22'),
       (10, 'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcS7aANlfA4_qPG-KsOCzgRXQ7Bw1XuKBY692qQYVkQYfA&s', 0,
        '2026-07-17 10:27:22');
INSERT INTO product_image (product_id, image_url, display_order, created_at)
VALUES (11, 'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcS9AhAVMPzhsFLFVkUWl37wjWzrFqoRoWvUNg77V4rcCA&s=10',
        0, '2026-07-17 10:27:22'),
       (12, 'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSYuYpiXCtH0yoLw979XHALlf42H1oXN2Pf3g-aicVKtQ&s=10',
        0, '2026-07-17 10:27:22'),
       (13, 'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSWHnJxzIeKF3vhV_aZYothMOMVB0cvclZ8uTXLHgO_pQ&s=10',
        0, '2026-07-17 10:27:22'),
       (14, 'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSUSqQZWF2r6mydndrWFvPexX526cEXZ-G-Xj7oCvgImQ&s', 0,
        '2026-07-17 10:27:22'),
       (15, 'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSf-r00u2kONOijG4OKdlnAoqz7D6_0pPAoKTx2C6EuBA&s=10',
        0, '2026-07-17 10:27:22'),
       (16, 'https://placehold.co/300x300?text=Wide+Denim+1', 0, '2026-07-17 10:27:22'),
       (17, 'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSmSjIpiNDuceAtJS8v96DRdpPsxntANLuj6GZYBq1S1A&s=10',
        0, '2026-07-17 10:27:22'),
       (18, 'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQVffFIXdggYGua2CbATOOCoy7ZpTOevgfOifyvALeEEQ&s=10',
        0, '2026-07-17 10:27:22'),
       (19, 'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSagZZ1eImQJWR9gK7KVN9Y07ASJYb-POddkg7q7IY6_w&s=10',
        0, '2026-07-17 10:27:22'),
       (20, 'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRy2BObnqfs2WuDbRtAJQ61qm03_XFnqbLW1DZlNezUlw&s', 0,
        '2026-07-17 10:27:22');
INSERT INTO product_image (product_id, image_url, display_order, created_at)
VALUES (21, 'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcR_Fg3RQFcxWSUGBBc-yP4Xki689tBYFyIBTgPtytYTmA&s=10',
        0, '2026-07-17 10:27:22'),
       (22, 'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRL4PRH6l2YlJMgg7PpFxioegplQ4ed0bYDDeY88mHx1w&s=10',
        0, '2026-07-17 10:27:22'),
       (23, 'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSzI8zQbFLoWJCn7JXEyKUiDeZe8G-Xx2EjxVT425s5Kg&s', 0,
        '2026-07-17 10:27:22'),
       (24, 'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTJd4guvPg-hqmMzaZt6ZdizVMl-D6JQh_0AN-WX8qiSA&s=10',
        0, '2026-07-17 10:27:22'),
       (25, 'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQk52u33HVXDFx8oH2llr2Yb7aYQAewI1mozlcFtP7gbw&s=10',
        0, '2026-07-17 10:27:22'),
       (26, 'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQ8b-wgeTmZXsFnI0_oBjYePa0Azfj1m-mWOgrmC9eXlg&s=10',
        0, '2026-07-17 10:27:22'),
       (27, 'https://placehold.co/300x300?text=Bucketbag+1', 0, '2026-07-17 10:27:22'),
       (28, 'https://m.chaakan.co.kr/web/product/extra/small/202407/3f2d10c6ee68ae977d16df7ed9d8e583.jpg', 0,
        '2026-07-17 10:27:22'),
       (29, 'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTQ5nSkOLrkKlGfemXNhFngcpVgdnXEi_byG07pFICG4Q&s=10',
        0, '2026-07-17 10:27:22'),
       (30, 'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQuOFaBwbjmPA_SGS7JylH7pvk55bznwksA8RuIc-wHUQ&s=10',
        0, '2026-07-17 10:27:22');
INSERT INTO product_image (product_id, image_url, display_order, created_at)
VALUES (31, 'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRA5O4BgWL-aggZiC9ClGH7jNcV5QSHVJaasuuVxyVqbQ&s=10',
        0, '2026-07-17 10:27:22'),
       (32,
        'https://img.danuri.io/catalog-image/435/425/000/9af5516c0bd646249b1a65b93169959b.jpg?shrink=500:*&_v=20260719101058',
        0, '2026-07-17 10:27:22'),
       (33,
        'https://img.ssfshop.com/cmd/LB_750x1000/src/https://img.ssfshop.com/goods/REFA/26/05/19/GPMY26051905484_0_ORGINL_20260601174409054.jpg',
        0, '2026-07-17 10:27:22'),
       (34, 'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTvWur1b8omlfByb5iCSxOLWWQe71Y_0yjsoBAxeRyCcA&s=10',
        0, '2026-07-17 10:27:22'),
       (35, 'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQWI-tsMArvHxZEbNrB8VVyZoCwzh1WZi5o6sUE8Y_FHg&s', 0,
        '2026-07-17 10:27:22'),
       (36, 'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRutp97L7jzn-DoBSzEdBoXv7R4QZj4W8ATAFzx2VrBdQ&s', 0,
        '2026-07-17 10:27:22'),
       (2, 'https://placehold.co/300x300?text=Shirt+2', 1, '2026-07-17 10:27:25'),
       (4, 'https://placehold.co/300x300?text=Coat+2', 1, '2026-07-17 10:27:25'),
       (6, 'https://placehold.co/300x300?text=Linen+Shirt+2', 1, '2026-07-17 10:27:25'),
       (8, 'https://placehold.co/300x300?text=Cardigan+2', 1, '2026-07-17 10:27:25');
INSERT INTO product_image (product_id, image_url, display_order, created_at)
VALUES (10, 'https://placehold.co/300x300?text=Wide+Pants+2', 1, '2026-07-17 10:27:25'),
       (12, 'https://placehold.co/300x300?text=Slacks+2', 1, '2026-07-17 10:27:25'),
       (14, 'https://placehold.co/300x300?text=Skirt+2', 1, '2026-07-17 10:27:25'),
       (16, 'https://placehold.co/300x300?text=Wide+Denim+2', 1, '2026-07-17 10:27:25'),
       (18, 'https://placehold.co/300x300?text=Corduroy+2', 1, '2026-07-17 10:27:25'),
       (20, 'https://placehold.co/300x300?text=Backpack+2', 1, '2026-07-17 10:27:25'),
       (22, 'https://placehold.co/300x300?text=Shoulderbag+2', 1, '2026-07-17 10:27:25'),
       (24, 'https://placehold.co/300x300?text=Slingbag+2', 1, '2026-07-17 10:27:25'),
       (26, 'https://placehold.co/300x300?text=Dufflebag+2', 1, '2026-07-17 10:27:25'),
       (28, 'https://placehold.co/300x300?text=Sneakers+2', 1, '2026-07-17 10:27:25');
INSERT INTO product_image (product_id, image_url, display_order, created_at)
VALUES (30, 'https://placehold.co/300x300?text=Running+Shoes+2', 1, '2026-07-17 10:27:25'),
       (32, 'https://placehold.co/300x300?text=Hightop+2', 1, '2026-07-17 10:27:25'),
       (34, 'https://placehold.co/300x300?text=Sandals+2', 1, '2026-07-17 10:27:25'),
       (36, 'https://placehold.co/300x300?text=Slipon+2', 1, '2026-07-17 10:27:25'),
       (4, 'https://placehold.co/300x300?text=Coat+3', 2, '2026-07-17 10:27:27'),
       (8, 'https://placehold.co/300x300?text=Cardigan+3', 2, '2026-07-17 10:27:27'),
       (12, 'https://placehold.co/300x300?text=Slacks+3', 2, '2026-07-17 10:27:27'),
       (16, 'https://placehold.co/300x300?text=Wide+Denim+3', 2, '2026-07-17 10:27:27'),
       (20, 'https://placehold.co/300x300?text=Backpack+3', 2, '2026-07-17 10:27:27'),
       (24, 'https://placehold.co/300x300?text=Slingbag+3', 2, '2026-07-17 10:27:27');
INSERT INTO product_image (product_id, image_url, display_order, created_at)
VALUES (28, 'https://placehold.co/300x300?text=Sneakers+3', 2, '2026-07-17 10:27:27'),
       (32, 'https://placehold.co/300x300?text=Hightop+3', 2, '2026-07-17 10:27:27'),
       (36, 'https://placehold.co/300x300?text=Slipon+3', 2, '2026-07-17 10:27:27');
INSERT INTO product_option (product_id, name, extra_price, stock_quantity, display_order, is_active, created_at,
                                    updated_at)
VALUES (1, 'S', 0, 40, 1, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (1, 'M', 0, 40, 2, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (1, 'L', 1000, 0, 3, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (2, 'S', 0, 30, 1, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (2, 'M', 0, 30, 2, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (2, 'L', 1000, 25, 3, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (3, 'S', 0, 70, 1, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (3, 'M', 0, 70, 2, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (3, 'L', 1000, 60, 3, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (4, 'S', 0, 15, 1, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41');
INSERT INTO product_option (product_id, name, extra_price, stock_quantity, display_order, is_active, created_at,
                                    updated_at)
VALUES (4, 'M', 0, 15, 2, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (4, 'L', 1000, 10, 3, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (5, 'S', 0, 35, 1, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (5, 'M', 0, 35, 2, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (5, 'L', 1000, 25, 3, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (6, 'S', 0, 0, 1, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (6, 'M', 0, 0, 2, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (6, 'L', 1000, 0, 3, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (7, 'S', 0, 50, 1, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (7, 'M', 0, 50, 2, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41');
INSERT INTO product_option (product_id, name, extra_price, stock_quantity, display_order, is_active, created_at,
                                    updated_at)
VALUES (7, 'L', 1000, 50, 3, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (8, 'S', 0, 20, 1, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (8, 'M', 0, 20, 2, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (8, 'L', 1000, 20, 3, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (9, 'S', 0, 100, 1, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (9, 'M', 0, 100, 2, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (9, 'L', 1000, 100, 3, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (10, 'S', 0, 40, 1, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (10, 'M', 0, 40, 2, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (10, 'L', 1000, 30, 3, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41');
INSERT INTO product_option (product_id, name, extra_price, stock_quantity, display_order, is_active, created_at,
                                    updated_at)
VALUES (11, 'S', 0, 45, 1, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (11, 'M', 0, 45, 2, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (11, 'L', 1000, 40, 3, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (12, 'S', 0, 25, 1, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (12, 'M', 0, 25, 2, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (12, 'L', 1000, 20, 3, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (13, 'S', 0, 55, 1, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (13, 'M', 0, 55, 2, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (13, 'L', 1000, 50, 3, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (14, 'S', 0, 30, 1, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41');
INSERT INTO product_option (product_id, name, extra_price, stock_quantity, display_order, is_active, created_at,
                                    updated_at)
VALUES (14, 'M', 0, 30, 2, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (14, 'L', 1000, 30, 3, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (15, 'S', 0, 50, 1, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (15, 'M', 0, 50, 2, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (15, 'L', 1000, 40, 3, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (16, 'S', 0, 0, 1, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (16, 'M', 0, 0, 2, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (16, 'L', 1000, 0, 3, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (17, 'S', 0, 25, 1, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (17, 'M', 0, 25, 2, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41');
INSERT INTO product_option (product_id, name, extra_price, stock_quantity, display_order, is_active, created_at,
                                    updated_at)
VALUES (17, 'L', 1000, 25, 3, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (18, 'S', 0, 35, 1, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (18, 'M', 0, 35, 2, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (18, 'L', 1000, 35, 3, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (28, '250', 0, 60, 1, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (28, '260', 0, 60, 2, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (28, '270', 0, 60, 3, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (29, '250', 0, 15, 1, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (29, '260', 0, 15, 2, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (29, '270', 0, 10, 3, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41');
INSERT INTO product_option (product_id, name, extra_price, stock_quantity, display_order, is_active, created_at,
                                    updated_at)
VALUES (30, '250', 0, 50, 1, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (30, '260', 0, 50, 2, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (30, '270', 0, 45, 3, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (31, '250', 0, 20, 1, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (31, '260', 0, 20, 2, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (31, '270', 0, 15, 3, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (32, '250', 0, 55, 1, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (32, '260', 0, 60, 2, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (32, '270', 0, 55, 3, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (33, '250', 0, 20, 1, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41');
INSERT INTO product_option (product_id, name, extra_price, stock_quantity, display_order, is_active, created_at,
                                    updated_at)
VALUES (33, '260', 0, 25, 2, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (33, '270', 0, 20, 3, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (34, '250', 0, 30, 1, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (34, '260', 0, 30, 2, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (34, '270', 0, 30, 3, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (35, '250', 0, 10, 1, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (35, '260', 0, 15, 2, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (35, '270', 0, 10, 3, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (36, '250', 0, 65, 1, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41'),
       (36, '260', 0, 70, 2, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41');
INSERT INTO product_option (product_id, name, extra_price, stock_quantity, display_order, is_active, created_at,
                                    updated_at)
VALUES (36, '270', 0, 65, 3, 1, '2026-07-19 07:48:41', '2026-07-19 07:48:41');
