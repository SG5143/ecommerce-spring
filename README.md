# 이커머스 도메인 학습

[![CI 빌드 및 테스트](https://github.com/SG5143/ecommerce-spring/actions/workflows/ci.yml/badge.svg)](https://github.com/SG5143/ecommerce-spring/actions/workflows/ci.yml)

Java 25와 Spring Boot 기반으로 개발하는 이커머스 프로젝트입니다.<br>
주문·결제·재고 처리 과정을 다루고 학습합니다. + 테스트와 배포를 자동화 예정입니다.

## 현재 구현 기능 (2026.07.30)

- JWT 기반 로그인, 토큰 재발급 및 로그아웃
- 회원가입, 회원 정보 조회·수정·탈퇴와 Argon2id 비밀번호 해싱
- 카테고리별 상품 목록 및 상품 상세 조회
- 회원·비회원 장바구니와 로그인 후 장바구니 병합
- 주문 스냅샷, 옵션 재고 예약·복구와 결제 멱등 처리
- 토스페이먼츠 V2 Standard Payment Window 테스트 결제와 결과 재조정
- Flyway 기반 MySQL 스키마 형상 관리와 JPA 스키마 검증
- GitHub Actions의 MySQL 8.0 Service Container 기반 자동 테스트

## 기술 스택

| 구분 | 기술                                                    |
| --- |-------------------------------------------------------|
| Backend | Java 25, Spring Boot 4.1, Spring MVC, Spring Data JPA |
| Database | MySQL 8.0, Flyway, Hibernate                          |
| Security | Spring Security, JWT, Argon2id                        |
| Cache | Caffeine                                              |
| View | Thymeleaf, Vanilla JS, CSS                            |
| Test & Build | JUnit 5, Mockito, Gradle                              |
| CI/CD | GitHub Actions, Docker(예정)                            |

## 핵심 설계 정책

주문·결제 도메인에는 다음 정책을 적용합니다.

- **상품 스냅샷:** 주문서가 생성될 때 상품명, 단가, 카테고리를 저장하고 결제 요청 금액을 서버에서 다시 검증
- **재고 차감:** 주문 단계에서는 차감하지 않고 승인 준비 트랜잭션에서 예약한 뒤 확정 실패 시 복구
- **중복 결제 방지:** DB 유니크 제약과 Caffeine 보조 차단기를 함께 사용해 동일 결제 요청을 처리
- **상태 전이:** 정의된 주문·결제 상태 전이만 허용하며 배송 시작 이후의 주문 취소를 차단
- **실패 복구:** 결제 실패 시 재고를 원상 복구하고 장바구니 상품은 미삭제
- **외부 승인 분리:** PG HTTP 호출은 DB 트랜잭션 밖에서 실행하고 불명확한 결과는 PROCESSING으로 재조정

## 토스 테스트 결제 설정

기본 제공사는 `virtual`이며, 토스 테스트 결제는 같은 API 키 세트의 환경변수를 주입해 실행합니다.

```shell
PAYMENT_PROVIDER=toss
TOSS_CLIENT_KEY=발급받은_테스트_클라이언트_키
TOSS_SECRET_KEY=발급받은_테스트_시크릿_키
```

승인 오류를 재현할 때만 local 프로필에서 `TOSS_TEST_CODE`를 추가합니다. 시크릿 키는 브라우저나 저장소에 노출하지 않습니다.

## 개발 로드맵

| 기간 | 주요 계획 | 상태 |
| --- | --- | --- |
| Week 1 | Flyway·CI 구축, 주문/결제 테이블과 주문서 스냅샷 설계 | 진행 중 |
| Week 2 | 가상 결제, 재고 차감, 실패 복구, 장바구니 부분 삭제, 결제 멱등성 | 완료 |
| Week 3 | 토스 테스트 결제창, 승인 트랜잭션 분리, 결제 재조정 | 완료 |
| Week 4 | JPA 동시성 락 비교, Locust 부하 테스트, Docker/Compose 환경 구축 | 예정 |
| Week 5 | 관리자 권한, 상품·주문 관리, 상태 변경 감사 로그 | 예정 |
| Week 6 | 통합·부하 테스트, 성능 개선, 보안 검토 및 문서화 | 예정 |


## 구현 범위 제외

- 토스 라이브 키 실결제, 환불 API와 부분 취소
- 실제 가상계좌 입금과 웹훅 처리
- 택배사 및 파일 스토리지 외부 연동
