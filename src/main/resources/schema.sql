-- mingler.`member` definition

CREATE TABLE `member`
(
    `id`                   bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '고유식별자',
    `username`             varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '아이디',
    `password`             varchar(255) COLLATE utf8mb4_unicode_ci                      NOT NULL COMMENT '비밀번호',
    `name`                 varchar(50) COLLATE utf8mb4_unicode_ci                       NOT NULL COMMENT '실명',
    `phone`                varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '휴대폰',
    `phone_verified_at`    datetime                                                              DEFAULT NULL COMMENT '휴대폰 본인확인 완료일시 (미인증 시 NULL)',
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
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='회원 정보';


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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Refresh 토큰 (회전형, 재사용 탐지)';