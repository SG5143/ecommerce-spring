# Day 12 재고 잠금 성능 비교

비회원 주문 생성 → 가상결제 준비 → 동일 상품 옵션 동시 승인 API를 실행한다. 운영 코드는 비관적 쓰기 잠금을 사용하며, 낙관적 잠금은 별도 실험 작업공간에서만 비교한다.

## 안전 조건

- 운영 DB나 개인 개발 DB가 아닌 별도 MySQL 데이터베이스에서만 실행한다.
- 단일 Locust 프로세스로 실행한다. 승인 장벽은 분산 worker 사이에서 공유되지 않는다.
- `CONCURRENT_USERS`와 Locust `--users`는 같아야 한다.
- HikariCP 최대 크기는 모든 비교 실행에서 20으로 고정한다.
- `loadtest/results/`의 CSV와 지표 파일은 Git에 포함하지 않는다.

## 1. 도구와 애플리케이션 준비

```shell
python3 -m venv .venv-locust
.venv-locust/bin/python -m pip install -r loadtest/requirements.txt
mkdir -p loadtest/results
```

전용 DB와 가상결제를 사용해 애플리케이션을 실행한다.

```shell
SPRING_PROFILES_ACTIVE=loadtest \
SPRING_DATASOURCE_URL='jdbc:mysql://localhost:3306/mingler_loadtest?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul' \
SPRING_DATASOURCE_USERNAME=loadtest \
SPRING_DATASOURCE_PASSWORD=loadtest \
SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=20 \
PAYMENT_PROVIDER=virtual \
./gradlew bootRun
```

테스트할 판매 중 상품과 활성 옵션을 조회한다.

```sql
SELECT p.id AS product_id, o.id AS option_id, p.name, o.name, o.stock_quantity
FROM product p
JOIN product_option o ON o.product_id = p.id
WHERE p.status = 'ON_SALE' AND o.is_active = 1
ORDER BY p.id, o.id;
```

## 2. 실행 식별자와 DB 준비

`EXPECTED_LOCK_MODE=pessimistic`, `RUN_ID=limited-u20-r1`이면 주문자명은 `LOCUST_DAY12_pessimistic_limited-u20-r1`이다. SQL의 `@run_marker`에 같은 값을 사용한다.

```shell
mysql -h 127.0.0.1 -u loadtest -p mingler_loadtest \
  --execute="SET @target_option_id=1; SET @initial_stock=5; SET @run_marker='LOCUST_DAY12_pessimistic_limited-u20-r1'; SOURCE loadtest/sql/prepare.sql;"
```

실행 전 InnoDB 행 잠금 지표를 TSV로 저장한다.

```shell
mysql -h 127.0.0.1 -u loadtest -p --batch mingler_loadtest \
  < loadtest/sql/lock_metrics.sql \
  > loadtest/results/pessimistic-limited-u20-r1-before.tsv
```

## 3. Locust 실행

아래 예시는 비관적 락, 사용자 20명, 초기 재고 5개 조건이다.

```shell
EXPECTED_LOCK_MODE=pessimistic \
RUN_ID=limited-u20-r1 \
TARGET_PRODUCT_ID=1 \
TARGET_OPTION_ID=1 \
CONCURRENT_USERS=20 \
INITIAL_STOCK=5 \
BARRIER_TIMEOUT_SECONDS=30 \
CSV_FLUSH_GRACE_SECONDS=6 \
.venv-locust/bin/locust \
  -f loadtest/locustfile.py \
  --headless \
  --host http://localhost:8080 \
  --users 20 \
  --spawn-rate 20 \
  --run-time 60s \
  --csv loadtest/results/pessimistic-limited-u20-r1 \
  --only-summary
```

모든 사용자는 주문·준비·승인을 한 번씩 실행한다. 마지막 사용자가 완료되면 Locust CSV writer의 통계 갱신과 flush를 위해 6초 후 프로세스를 종료한다.

## 4. CSV와 DB 불변식 검증

각 엔드포인트 요청 수와 전체 요청 수를 검사한다.

```shell
.venv-locust/bin/python loadtest/verify_csv.py \
  loadtest/results/pessimistic-limited-u20-r1_stats.csv \
  --users 20
```

DB 검증에서 다음 두 값이 모두 `1`이어야 한다.

```text
stock_conservation_passed = successful_payments + final_stock == initial_stock
no_oversell_passed = successful_payments <= initial_stock
```

```shell
mysql -h 127.0.0.1 -u loadtest -p mingler_loadtest \
  --execute="SET @target_option_id=1; SET @initial_stock=5; SET @run_marker='LOCUST_DAY12_pessimistic_limited-u20-r1'; SOURCE loadtest/sql/verify.sql;"
```

실행 후 지표를 저장하고 차이를 계산한다.

```shell
mysql -h 127.0.0.1 -u loadtest -p --batch mingler_loadtest \
  < loadtest/sql/lock_metrics.sql \
  > loadtest/results/pessimistic-limited-u20-r1-after.tsv

.venv-locust/bin/python loadtest/compare_lock_metrics.py \
  loadtest/results/pessimistic-limited-u20-r1-before.tsv \
  loadtest/results/pessimistic-limited-u20-r1-after.tsv
```

전용 MySQL에 다른 작업이 실행되면 전역 InnoDB 지표 차이에 해당 작업의 대기시간도 포함된다.

## 5. 데이터 정리

`@restore_stock`은 테스트 전에 기록한 재고다. `NULL`이면 주문·결제만 삭제한다.

```shell
mysql -h 127.0.0.1 -u loadtest -p mingler_loadtest \
  --execute="SET @target_option_id=1; SET @run_marker='LOCUST_DAY12_pessimistic_limited-u20-r1'; SET @restore_stock=20; SOURCE loadtest/sql/cleanup.sql;"
```

## 6. 공식 비교 매트릭스

각 잠금 방식에서 20명 워밍업을 먼저 실행한다. 본 측정은 각 조건을 3회 실행하고 세 실행의 중앙값을 사용한다.

| 시나리오 | 사용자 | 초기 재고 | 비관적 락 기대 결과 |
| --- | ---: | ---: | --- |
| 품절 경합 | 20 / 50 / 100 / 200 | 5 | 성공 5, 나머지 재고 부족 |
| 전체 성공 | 20 / 50 / 100 / 200 | 사용자 수 | 전원 성공 |

낙관적 실험에서는 `EXPECTED_LOCK_MODE=optimistic`을 사용한다. 재시도 소진 409는 `optimisticConflictRejected`로 집계하고, DB 불변식은 같은 SQL로 확인한다.

비교표에는 결제 승인 API의 평균·p50·p95·p99·최대 응답시간과 처리량, InnoDB 행 잠금 시간·대기 횟수, 성공·재고 부족·낙관적 충돌 소진 건수를 기록한다.

전체 매트릭스는 다음 명령으로 자동 실행할 수 있다. 각 실행의 준비·CSV 검증·DB 불변식 검증·정리 중 하나라도 실패하면 즉시 종료한다.

```shell
.venv-locust/bin/python loadtest/run_matrix.py \
  --lock-mode pessimistic \
  --database mingler_loadtest_pessimistic \
  --host-url http://127.0.0.1:8080 \
  --product-id 1 \
  --option-id 1
```

두 잠금 방식의 본 측정이 끝나면 결제 승인 API와 InnoDB 행 잠금 지표의 3회 중앙값 표를 생성한다.

```shell
.venv-locust/bin/python loadtest/summarize_results.py
```
