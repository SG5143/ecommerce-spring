-- ============================================================
-- Mingler 개발용 시드 데이터 (카테고리 6 + 상품 36 + 상품 이미지)
-- ============================================================

-- 1) 카테고리: 최상위 → 하위 순서 (셀프 FK)
INSERT INTO category (id, parent_id, name, display_order)
VALUES (1, NULL, '의류', 1),
       (2, NULL, '잡화', 2)
AS new
ON DUPLICATE KEY UPDATE parent_id = new.parent_id, name = new.name, display_order = new.display_order;

INSERT INTO category (id, parent_id, name, display_order)
VALUES (3, 1, '상의', 1),
       (4, 1, '하의', 2),
       (5, 2, '가방', 1),
       (6, 2, '신발', 2)
AS new
ON DUPLICATE KEY UPDATE parent_id = new.parent_id, name = new.name, display_order = new.display_order;

-- 2) 상품 36개 (리프 카테고리당 9개)
--    - created_at 을 상품마다 다른 일수로 명시해 "최신순 8개"가 결정적으로 나오게 함
--      → 메인화면 기대 결과(판매중 최신 8): id 36, 31, 26, 21, 11, 1, 32, 22
--      → id 16, 6, 27 은 최신이지만 SOLD_OUT 이라 제외되는지 확인용
INSERT INTO product
    (id, category_id, name, description, price, sale_price, stock_quantity, status, thumbnail_url, view_count, sales_count, created_at)
VALUES
    -- 상의 (category 3)
    (1,  3, '캐시미어 블렌드 라운드넥 니트', '부드러운 캐시미어 혼방 소재의 데일리 니트. 목선이 깔끔한 라운드넥 실루엣.', 89000, 69000, 120, 'ON_SALE',  'https://placehold.co/300x300?text=Knit+1',        1543, 213, DATE_SUB(NOW(), INTERVAL 7 DAY)),
    (2,  3, '스트라이프 옥스포드 셔츠',      '탄탄한 옥스포드 원단에 클래식한 스트라이프 패턴을 더한 셔츠.',                45000, NULL,   85, 'ON_SALE',  'https://placehold.co/300x300?text=Shirt+1',       872,  96,  DATE_SUB(NOW(), INTERVAL 14 DAY)),
    (3,  3, '베이식 코튼 맨투맨',            '순면 100% 기본 맨투맨. 어떤 하의와도 잘 어울리는 무난한 핏.',                 32000, 25900, 200, 'ON_SALE',  'https://placehold.co/300x300?text=Sweatshirt+1',  2310, 428, DATE_SUB(NOW(), INTERVAL 21 DAY)),
    (4,  3, '울 블렌드 오버핏 코트',         '겨울 시즌 대표 아우터. 울 혼방 소재로 보온성과 실루엣을 모두 잡은 오버핏 코트.', 158000, NULL, 40, 'ON_SALE',  'https://placehold.co/300x300?text=Coat+1',        1980, 154, DATE_SUB(NOW(), INTERVAL 28 DAY)),
    (5,  3, '소프트 터틀넥 스웨터',          '까슬거림 없는 소프트 아크릴 혼방 터틀넥. 단독으로도 이너로도 활용도 높음.',      54000, 43900, 95, 'ON_SALE',  'https://placehold.co/300x300?text=Turtleneck+1',  654,  71,  DATE_SUB(NOW(), INTERVAL 35 DAY)),
    (6,  3, '린넨 반팔 셔츠',                '여름용 시원한 린넨 혼방 반팔 셔츠. 자연스러운 구김이 매력.',                   39000, NULL,    0, 'SOLD_OUT', 'https://placehold.co/300x300?text=Linen+Shirt+1', 1120, 302, DATE_SUB(NOW(), INTERVAL 6 DAY)),
    (7,  3, '후드 집업 자켓',                '간절기 활용도 높은 기모 안감 후드 집업.',                                     59000, NULL,  150, 'ON_SALE',  'https://placehold.co/300x300?text=Hoodie+1',      930,  118, DATE_SUB(NOW(), INTERVAL 13 DAY)),
    (8,  3, '램스울 브이넥 가디건',          '램스울 혼방의 클래식 브이넥 가디건. 오피스룩에도 잘 어울림.',                  72000, 59000, 60, 'ON_SALE',  'https://placehold.co/300x300?text=Cardigan+1',    445,  38,  DATE_SUB(NOW(), INTERVAL 20 DAY)),
    (9,  3, '헤비웨이트 무지 티셔츠',        '두툼한 헤비웨이트 코튼으로 형태 유지력이 좋은 무지 티셔츠.',                   19000, NULL,  300, 'ON_SALE',  'https://placehold.co/300x300?text=Tshirt+1',      3105, 892, DATE_SUB(NOW(), INTERVAL 27 DAY)),
    -- 하의 (category 4)
    (10, 4, '코튼 와이드 밴딩 팬츠',         '허리 전체 밴딩으로 편안한 착용감의 코튼 와이드 팬츠.',                        49000, NULL,  110, 'ON_SALE',  'https://placehold.co/300x300?text=Wide+Pants+1',  1287, 245, DATE_SUB(NOW(), INTERVAL 34 DAY)),
    (11, 4, '슬림 스트레이트 데님',          '허벅지는 여유 있고 밑단은 곧게 떨어지는 슬림 스트레이트 핏 데님.',              55000, 46900, 130, 'ON_SALE',  'https://placehold.co/300x300?text=Denim+1',       1765, 331, DATE_SUB(NOW(), INTERVAL 5 DAY)),
    (12, 4, '울 슬랙스',                     '겨울용 울 혼방 슬랙스. 세미와이드 핏으로 격식과 편안함을 동시에.',              68000, NULL,   70, 'ON_SALE',  'https://placehold.co/300x300?text=Slacks+1',      542,  49,  DATE_SUB(NOW(), INTERVAL 12 DAY)),
    (13, 4, '카고 조거 팬츠',                '입체 포켓 디테일의 스트리트 무드 카고 조거.',                                 43000, NULL,  160, 'ON_SALE',  'https://placehold.co/300x300?text=Cargo+Pants+1', 998,  187, DATE_SUB(NOW(), INTERVAL 19 DAY)),
    (14, 4, '하이웨이스트 플리츠 스커트',    '잔잔한 플리츠가 돋보이는 하이웨이스트 미디 스커트.',                          38000, 29900, 90, 'ON_SALE',  'https://placehold.co/300x300?text=Skirt+1',       1432, 276, DATE_SUB(NOW(), INTERVAL 26 DAY)),
    (15, 4, '스트레치 치노 팬츠',            '신축성 좋은 스트레치 치노. 출근룩 기본템.',                                   41000, NULL,  140, 'ON_SALE',  'https://placehold.co/300x300?text=Chino+1',       765,  132, DATE_SUB(NOW(), INTERVAL 33 DAY)),
    (16, 4, '와이드 데님 팬츠',              '과감하게 떨어지는 와이드 실루엣 데님.',                                       57000, NULL,    0, 'SOLD_OUT', 'https://placehold.co/300x300?text=Wide+Denim+1',  2087, 415, DATE_SUB(NOW(), INTERVAL 4 DAY)),
    (17, 4, '벨티드 버뮤다 쇼츠',            '동일 원단 벨트가 세트인 버뮤다 기장 쇼츠.',                                   33000, NULL,   75, 'ON_SALE',  'https://placehold.co/300x300?text=Shorts+1',      389,  44,  DATE_SUB(NOW(), INTERVAL 11 DAY)),
    (18, 4, '코듀로이 테이퍼드 팬츠',        '가을·겨울 시즌 코듀로이 소재의 테이퍼드 핏 팬츠.',                            47000, 39900, 105, 'ON_SALE',  'https://placehold.co/300x300?text=Corduroy+1',    623,  87,  DATE_SUB(NOW(), INTERVAL 18 DAY)),
    -- 가방 (category 5)
    (19, 5, '미니멀 레더 크로스백',          '군더더기 없는 디자인의 소가죽 크로스백. 데일리로 부담 없는 사이즈.',            72000, 59900, 55, 'ON_SALE',  'https://placehold.co/300x300?text=Crossbag+1',    1876, 298, DATE_SUB(NOW(), INTERVAL 25 DAY)),
    (20, 5, '데일리 캔버스 백팩',            '노트북 수납이 가능한 넉넉한 용량의 캔버스 백팩.',                             65000, NULL,   80, 'ON_SALE',  'https://placehold.co/300x300?text=Backpack+1',    1345, 221, DATE_SUB(NOW(), INTERVAL 32 DAY)),
    (21, 5, '클래식 브리프케이스',           '비즈니스 룩을 완성하는 클래식 무드의 브리프케이스.',                          128000, NULL,  30, 'ON_SALE',  'https://placehold.co/300x300?text=Briefcase+1',   456,  27,  DATE_SUB(NOW(), INTERVAL 3 DAY)),
    (22, 5, '퀼팅 체인 숄더백',              '볼륨감 있는 퀼팅과 골드 체인 스트랩의 숄더백.',                                98000, 79000, 45, 'ON_SALE',  'https://placehold.co/300x300?text=Shoulderbag+1', 2234, 187, DATE_SUB(NOW(), INTERVAL 10 DAY)),
    (23, 5, '에코 캔버스 토트백',            '가볍게 들기 좋은 코튼 캔버스 에코 토트백.',                                   24000, NULL,  250, 'ON_SALE',  'https://placehold.co/300x300?text=Totebag+1',     1678, 534, DATE_SUB(NOW(), INTERVAL 17 DAY)),
    (24, 5, '나일론 슬링백',                 '가벼운 나일론 소재의 캐주얼 슬링백. 여행용 보조가방으로도 좋음.',              36000, NULL,  120, 'ON_SALE',  'https://placehold.co/300x300?text=Slingbag+1',    843,  156, DATE_SUB(NOW(), INTERVAL 24 DAY)),
    (25, 5, '소프트 레더 클러치',            '부드러운 가죽 질감의 미니멀 클러치.',                                         58000, NULL,   35, 'ON_SALE',  'https://placehold.co/300x300?text=Clutch+1',      312,  19,  DATE_SUB(NOW(), INTERVAL 31 DAY)),
    (26, 5, '트래블 더플백',                 '1박 2일 여행에 알맞은 사이즈의 더플백. 숄더 스트랩 포함.',                     89000, 71000, 50, 'ON_SALE',  'https://placehold.co/300x300?text=Dufflebag+1',   721,  63,  DATE_SUB(NOW(), INTERVAL 2 DAY)),
    (27, 5, '미니 버킷백',                   '트렌디한 미니 사이즈 버킷백. 스트랩 길이 조절 가능.',                          62000, NULL,    0, 'SOLD_OUT', 'https://placehold.co/300x300?text=Bucketbag+1',   1954, 342, DATE_SUB(NOW(), INTERVAL 9 DAY)),
    -- 신발 (category 6)
    (28, 6, '클래식 코트 스니커즈',          '어디에나 어울리는 화이트 베이스의 클래식 코트 스니커즈.',                      79000, NULL,  180, 'ON_SALE',  'https://placehold.co/300x300?text=Sneakers+1',      2765, 612, DATE_SUB(NOW(), INTERVAL 16 DAY)),
    (29, 6, '스웨이드 첼시 부츠',            '부드러운 스웨이드 소재의 사이드 고어 첼시 부츠.',                             138000, 109000, 40, 'ON_SALE', 'https://placehold.co/300x300?text=Chelsea+Boots+1', 1123, 98, DATE_SUB(NOW(), INTERVAL 23 DAY)),
    (30, 6, '러닝 메시 스니커즈',            '통기성 좋은 메시 어퍼와 쿠셔닝 밑창의 러닝화.',                               88000, NULL,  145, 'ON_SALE',  'https://placehold.co/300x300?text=Running+Shoes+1', 1567, 234, DATE_SUB(NOW(), INTERVAL 30 DAY)),
    (31, 6, '레더 로퍼',                     '정장과 캐주얼을 넘나드는 클래식 페니 로퍼.',                                 115000, NULL,   55, 'ON_SALE',  'https://placehold.co/300x300?text=Loafer+1',        876,  67,  DATE_SUB(NOW(), INTERVAL 1 DAY)),
    (32, 6, '캔버스 하이탑 스니커즈',        '빈티지 무드의 캔버스 하이탑. 발목까지 감싸는 안정감.',                         49000, 39900, 170, 'ON_SALE',  'https://placehold.co/300x300?text=Hightop+1',       1998, 445, DATE_SUB(NOW(), INTERVAL 8 DAY)),
    (33, 6, '첼시 레인부츠',                 '비 오는 날에도 스타일리시한 첼시 스타일 레인부츠.',                            45000, NULL,   65, 'ON_SALE',  'https://placehold.co/300x300?text=Rainboots+1',     534,  72,  DATE_SUB(NOW(), INTERVAL 15 DAY)),
    (34, 6, '소프트 레더 샌들',              '푹신한 풋베드의 여름용 가죽 샌들.',                                           52000, NULL,   90, 'ON_SALE',  'https://placehold.co/300x300?text=Sandals+1',       687,  113, DATE_SUB(NOW(), INTERVAL 22 DAY)),
    (35, 6, '클래식 더비 슈즈',              '격식 있는 자리에 어울리는 클래식 더비 슈즈.',                                 129000, 99000, 35, 'ON_SALE',  'https://placehold.co/300x300?text=Derby+1',         398,  31,  DATE_SUB(NOW(), INTERVAL 29 DAY)),
    (36, 6, '라이트 슬립온',                 '끈 없이 편하게 신는 초경량 슬립온.',                                          42000, NULL,  200, 'ON_SALE',  'https://placehold.co/300x300?text=Slipon+1',        1245, 289, DATE_SUB(NOW(), INTERVAL 0 DAY))
AS new
ON DUPLICATE KEY UPDATE
    category_id    = new.category_id,
    name           = new.name,
    description    = new.description,
    price          = new.price,
    sale_price     = new.sale_price,
    stock_quantity = new.stock_quantity,
    status         = new.status,
    thumbnail_url  = new.thumbnail_url,
    view_count     = new.view_count,
    sales_count    = new.sales_count,
    created_at     = new.created_at;

-- 3) 상품 이미지
--    - display_order = 0 이 대표 이미지이며, product.thumbnail_url 과 동일한 URL (비정규화 정합성)
--    - 이미지 id 규칙: order 0 → 상품 id 그대로, order 1 → 100+상품id, order 2 → 200+상품id
-- 3-1) 대표 이미지 (전 상품, display_order = 0)
INSERT INTO product_image (id, product_id, image_url, display_order)
VALUES (1,  1,  'https://placehold.co/300x300?text=Knit+1',          0),
       (2,  2,  'https://placehold.co/300x300?text=Shirt+1',         0),
       (3,  3,  'https://placehold.co/300x300?text=Sweatshirt+1',    0),
       (4,  4,  'https://placehold.co/300x300?text=Coat+1',          0),
       (5,  5,  'https://placehold.co/300x300?text=Turtleneck+1',    0),
       (6,  6,  'https://placehold.co/300x300?text=Linen+Shirt+1',   0),
       (7,  7,  'https://placehold.co/300x300?text=Hoodie+1',        0),
       (8,  8,  'https://placehold.co/300x300?text=Cardigan+1',      0),
       (9,  9,  'https://placehold.co/300x300?text=Tshirt+1',        0),
       (10, 10, 'https://placehold.co/300x300?text=Wide+Pants+1',    0),
       (11, 11, 'https://placehold.co/300x300?text=Denim+1',         0),
       (12, 12, 'https://placehold.co/300x300?text=Slacks+1',        0),
       (13, 13, 'https://placehold.co/300x300?text=Cargo+Pants+1',   0),
       (14, 14, 'https://placehold.co/300x300?text=Skirt+1',         0),
       (15, 15, 'https://placehold.co/300x300?text=Chino+1',         0),
       (16, 16, 'https://placehold.co/300x300?text=Wide+Denim+1',    0),
       (17, 17, 'https://placehold.co/300x300?text=Shorts+1',        0),
       (18, 18, 'https://placehold.co/300x300?text=Corduroy+1',      0),
       (19, 19, 'https://placehold.co/300x300?text=Crossbag+1',      0),
       (20, 20, 'https://placehold.co/300x300?text=Backpack+1',      0),
       (21, 21, 'https://placehold.co/300x300?text=Briefcase+1',     0),
       (22, 22, 'https://placehold.co/300x300?text=Shoulderbag+1',   0),
       (23, 23, 'https://placehold.co/300x300?text=Totebag+1',       0),
       (24, 24, 'https://placehold.co/300x300?text=Slingbag+1',      0),
       (25, 25, 'https://placehold.co/300x300?text=Clutch+1',        0),
       (26, 26, 'https://placehold.co/300x300?text=Dufflebag+1',     0),
       (27, 27, 'https://placehold.co/300x300?text=Bucketbag+1',     0),
       (28, 28, 'https://placehold.co/300x300?text=Sneakers+1',      0),
       (29, 29, 'https://placehold.co/300x300?text=Chelsea+Boots+1', 0),
       (30, 30, 'https://placehold.co/300x300?text=Running+Shoes+1', 0),
       (31, 31, 'https://placehold.co/300x300?text=Loafer+1',        0),
       (32, 32, 'https://placehold.co/300x300?text=Hightop+1',       0),
       (33, 33, 'https://placehold.co/300x300?text=Rainboots+1',     0),
       (34, 34, 'https://placehold.co/300x300?text=Sandals+1',       0),
       (35, 35, 'https://placehold.co/300x300?text=Derby+1',         0),
       (36, 36, 'https://placehold.co/300x300?text=Slipon+1',        0)
AS new
ON DUPLICATE KEY UPDATE product_id = new.product_id, image_url = new.image_url, display_order = new.display_order;

-- 3-2) 추가 이미지 (짝수 id 상품, display_order = 1)
INSERT INTO product_image (id, product_id, image_url, display_order)
VALUES (102, 2,  'https://placehold.co/300x300?text=Shirt+2',         1),
       (104, 4,  'https://placehold.co/300x300?text=Coat+2',          1),
       (106, 6,  'https://placehold.co/300x300?text=Linen+Shirt+2',   1),
       (108, 8,  'https://placehold.co/300x300?text=Cardigan+2',      1),
       (110, 10, 'https://placehold.co/300x300?text=Wide+Pants+2',    1),
       (112, 12, 'https://placehold.co/300x300?text=Slacks+2',        1),
       (114, 14, 'https://placehold.co/300x300?text=Skirt+2',         1),
       (116, 16, 'https://placehold.co/300x300?text=Wide+Denim+2',    1),
       (118, 18, 'https://placehold.co/300x300?text=Corduroy+2',      1),
       (120, 20, 'https://placehold.co/300x300?text=Backpack+2',      1),
       (122, 22, 'https://placehold.co/300x300?text=Shoulderbag+2',   1),
       (124, 24, 'https://placehold.co/300x300?text=Slingbag+2',      1),
       (126, 26, 'https://placehold.co/300x300?text=Dufflebag+2',     1),
       (128, 28, 'https://placehold.co/300x300?text=Sneakers+2',      1),
       (130, 30, 'https://placehold.co/300x300?text=Running+Shoes+2', 1),
       (132, 32, 'https://placehold.co/300x300?text=Hightop+2',       1),
       (134, 34, 'https://placehold.co/300x300?text=Sandals+2',       1),
       (136, 36, 'https://placehold.co/300x300?text=Slipon+2',        1)
AS new
ON DUPLICATE KEY UPDATE product_id = new.product_id, image_url = new.image_url, display_order = new.display_order;

-- 3-3) 추가 이미지 (4의 배수 id 상품, display_order = 2)
INSERT INTO product_image (id, product_id, image_url, display_order)
VALUES (204, 4,  'https://placehold.co/300x300?text=Coat+3',          2),
       (208, 8,  'https://placehold.co/300x300?text=Cardigan+3',      2),
       (212, 12, 'https://placehold.co/300x300?text=Slacks+3',        2),
       (216, 16, 'https://placehold.co/300x300?text=Wide+Denim+3',    2),
       (220, 20, 'https://placehold.co/300x300?text=Backpack+3',      2),
       (224, 24, 'https://placehold.co/300x300?text=Slingbag+3',      2),
       (228, 28, 'https://placehold.co/300x300?text=Sneakers+3',      2),
       (232, 32, 'https://placehold.co/300x300?text=Hightop+3',       2),
       (236, 36, 'https://placehold.co/300x300?text=Slipon+3',        2)
AS new
ON DUPLICATE KEY UPDATE product_id = new.product_id, image_url = new.image_url, display_order = new.display_order;

-- 4) 상품 옵션
--    - 의류(상의·하의, 상품 1~18): S/M/L — L 은 빅사이즈 추가금 1,000원
--    - 신발(상품 28~36): 250/260/270 — 추가금 없음
--    - 가방(상품 19~27): 옵션 없음 (단일 구성 상품의 상세 화면 확인용)
--    - 옵션 id 규칙: 상품id*10 + display_order
--    - 재고 예외: 상품 1 의 L 은 0 (옵션 품절 표시 확인용), 품절 상품(6, 16)은 전 옵션 0
INSERT INTO product_option (id, product_id, name, extra_price, stock_quantity, display_order)
VALUES
    -- 상의 (상품 1~9)
    (11,  1,  'S',   0,    40, 1), (12,  1,  'M',   0,    40, 2), (13,  1,  'L',   1000, 0,  3),
    (21,  2,  'S',   0,    30, 1), (22,  2,  'M',   0,    30, 2), (23,  2,  'L',   1000, 25, 3),
    (31,  3,  'S',   0,    70, 1), (32,  3,  'M',   0,    70, 2), (33,  3,  'L',   1000, 60, 3),
    (41,  4,  'S',   0,    15, 1), (42,  4,  'M',   0,    15, 2), (43,  4,  'L',   1000, 10, 3),
    (51,  5,  'S',   0,    35, 1), (52,  5,  'M',   0,    35, 2), (53,  5,  'L',   1000, 25, 3),
    (61,  6,  'S',   0,    0,  1), (62,  6,  'M',   0,    0,  2), (63,  6,  'L',   1000, 0,  3),
    (71,  7,  'S',   0,    50, 1), (72,  7,  'M',   0,    50, 2), (73,  7,  'L',   1000, 50, 3),
    (81,  8,  'S',   0,    20, 1), (82,  8,  'M',   0,    20, 2), (83,  8,  'L',   1000, 20, 3),
    (91,  9,  'S',   0,   100, 1), (92,  9,  'M',   0,   100, 2), (93,  9,  'L',   1000, 100, 3),
    -- 하의 (상품 10~18)
    (101, 10, 'S',   0,    40, 1), (102, 10, 'M',   0,    40, 2), (103, 10, 'L',   1000, 30, 3),
    (111, 11, 'S',   0,    45, 1), (112, 11, 'M',   0,    45, 2), (113, 11, 'L',   1000, 40, 3),
    (121, 12, 'S',   0,    25, 1), (122, 12, 'M',   0,    25, 2), (123, 12, 'L',   1000, 20, 3),
    (131, 13, 'S',   0,    55, 1), (132, 13, 'M',   0,    55, 2), (133, 13, 'L',   1000, 50, 3),
    (141, 14, 'S',   0,    30, 1), (142, 14, 'M',   0,    30, 2), (143, 14, 'L',   1000, 30, 3),
    (151, 15, 'S',   0,    50, 1), (152, 15, 'M',   0,    50, 2), (153, 15, 'L',   1000, 40, 3),
    (161, 16, 'S',   0,    0,  1), (162, 16, 'M',   0,    0,  2), (163, 16, 'L',   1000, 0,  3),
    (171, 17, 'S',   0,    25, 1), (172, 17, 'M',   0,    25, 2), (173, 17, 'L',   1000, 25, 3),
    (181, 18, 'S',   0,    35, 1), (182, 18, 'M',   0,    35, 2), (183, 18, 'L',   1000, 35, 3),
    -- 신발 (상품 28~36)
    (281, 28, '250', 0,    60, 1), (282, 28, '260', 0,    60, 2), (283, 28, '270', 0,    60, 3),
    (291, 29, '250', 0,    15, 1), (292, 29, '260', 0,    15, 2), (293, 29, '270', 0,    10, 3),
    (301, 30, '250', 0,    50, 1), (302, 30, '260', 0,    50, 2), (303, 30, '270', 0,    45, 3),
    (311, 31, '250', 0,    20, 1), (312, 31, '260', 0,    20, 2), (313, 31, '270', 0,    15, 3),
    (321, 32, '250', 0,    55, 1), (322, 32, '260', 0,    60, 2), (323, 32, '270', 0,    55, 3),
    (331, 33, '250', 0,    20, 1), (332, 33, '260', 0,    25, 2), (333, 33, '270', 0,    20, 3),
    (341, 34, '250', 0,    30, 1), (342, 34, '260', 0,    30, 2), (343, 34, '270', 0,    30, 3),
    (351, 35, '250', 0,    10, 1), (352, 35, '260', 0,    15, 2), (353, 35, '270', 0,    10, 3),
    (361, 36, '250', 0,    65, 1), (362, 36, '260', 0,    70, 2), (363, 36, '270', 0,    65, 3)
AS new
ON DUPLICATE KEY UPDATE
    product_id     = new.product_id,
    name           = new.name,
    extra_price    = new.extra_price,
    stock_quantity = new.stock_quantity,
    display_order  = new.display_order;
