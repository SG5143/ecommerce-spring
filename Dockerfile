# syntax=docker/dockerfile:1.7

# jdk25 유분투 noble기반 이미지
FROM eclipse-temurin:25-jdk-noble AS builder

# 작업 디렉토리 설정
WORKDIR /workspace

# Gradle 관련 파일 복사
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle

# Gradle 파일 실행권한 부여
RUN chmod +x gradlew

# 소스 코드 복사
COPY src ./src

# Gradle 캐시를 /root/.gradle에 연결 -> Gradle daemon 없이 bootJar 실행 -> JAR 생성
# Gradle 의존성을 매 빌드마다 다시 다운로드하지 않도록 캐시 사용
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon bootJar

# Flyway 마이그레이션용 이미지
FROM redgate/flyway:12.4.0 AS migrator

# 프로젝트 SQL 파일을 Flyway 기본 SQL 디렉터리로 복사
COPY src/main/resources/db/migration /flyway/sql

# 빌드는 끝나서 JRE만 사용
FROM eclipse-temurin:25-jre-noble AS runtime

# 패키지 목록 갱신 -> curl 자동 설치 -> 패키지 목록 캐시 삭제 -> GID가 10001인 mingler 그룹 생성
# UID가 10001으로 생성된 사용자는 홈디렉터리 생성 x 로그인 불가능 설정
RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 mingler \
    && useradd --uid 10001 --gid 10001 --no-create-home --shell /usr/sbin/nologin mingler

# 작업 디렉토리 설정
WORKDIR /app

# builder 단계에서 생성한 app.jar만 runtime 이미지로 복사
# 파일 소유자는 사용자(UID 10001), 그룹(GID 10001)
COPY --from=builder --chown=10001:10001 /workspace/build/libs/app.jar ./app.jar

# 전용 사용자로 실행
USER 10001:10001

# 8080 포트를 사용
EXPOSE 8080

# 컨테이너가 시작되면 애플리케이션 실행
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
