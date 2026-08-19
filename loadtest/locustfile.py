import logging
import os
import re
import uuid

import gevent
from gevent.event import Event
from gevent.lock import Semaphore
from locust import HttpUser, between, events, task
from locust.exception import StopUser


LOGGER = logging.getLogger(__name__)
SUPPORTED_LOCK_MODES = {"pessimistic", "optimistic"}


def positive_int(name, default=None):
    raw_value = os.getenv(name)
    if raw_value is None:
        if default is None:
            raise ValueError(f"{name} 환경변수가 필요합니다.")
        return default
    try:
        value = int(raw_value)
    except ValueError as error:
        raise ValueError(f"{name}은 정수여야 합니다.") from error
    if value <= 0:
        raise ValueError(f"{name}은 0보다 커야 합니다.")
    return value


def positive_float(name, default):
    raw_value = os.getenv(name, str(default))
    try:
        value = float(raw_value)
    except ValueError as error:
        raise ValueError(f"{name}은 숫자여야 합니다.") from error
    if value <= 0:
        raise ValueError(f"{name}은 0보다 커야 합니다.")
    return value


def run_marker():
    raw_run_label = os.getenv("RUN_LABEL", "DAY12")
    if len(raw_run_label) > 6:
        raise ValueError("RUN_LABEL은 6자 이하여야 합니다.")
    normalized_run_label = re.sub(r"[^A-Za-z0-9_-]", "_", raw_run_label)
    if not normalized_run_label:
        raise ValueError("RUN_LABEL에는 영문, 숫자, 밑줄 또는 하이픈이 하나 이상 필요합니다.")
    raw_run_id = os.getenv("RUN_ID", uuid.uuid4().hex[:12])
    normalized_run_id = re.sub(r"[^A-Za-z0-9_-]", "_", raw_run_id)[:24]
    if not normalized_run_id:
        raise ValueError("RUN_ID에는 영문, 숫자, 밑줄 또는 하이픈이 하나 이상 필요합니다.")
    marker = f"LOCUST_{normalized_run_label}_{EXPECTED_LOCK_MODE}_{normalized_run_id}"
    if len(marker) > 50:
        raise ValueError("실행 마커는 주문자명 제한에 맞게 50자 이하여야 합니다.")
    return marker


def lock_mode():
    value = os.getenv("EXPECTED_LOCK_MODE", "pessimistic").strip().lower()
    if value not in SUPPORTED_LOCK_MODES:
        supported = ", ".join(sorted(SUPPORTED_LOCK_MODES))
        raise ValueError(f"EXPECTED_LOCK_MODE은 다음 중 하나여야 합니다: {supported}")
    return value


CONFIGURATION_ERRORS = []
try:
    EXPECTED_LOCK_MODE = lock_mode()
    TARGET_PRODUCT_ID = positive_int("TARGET_PRODUCT_ID")
    TARGET_OPTION_ID = positive_int("TARGET_OPTION_ID")
    CONCURRENT_USERS = positive_int("CONCURRENT_USERS", 20)
    INITIAL_STOCK = positive_int("INITIAL_STOCK", 5)
    BARRIER_TIMEOUT_SECONDS = positive_float("BARRIER_TIMEOUT_SECONDS", 30)
    CSV_FLUSH_GRACE_SECONDS = positive_float("CSV_FLUSH_GRACE_SECONDS", 6)
    RUN_MARKER = run_marker()
except ValueError as configuration_error:
    CONFIGURATION_ERRORS.append(str(configuration_error))
    EXPECTED_LOCK_MODE = "pessimistic"
    TARGET_PRODUCT_ID = 0
    TARGET_OPTION_ID = 0
    CONCURRENT_USERS = 20
    INITIAL_STOCK = 5
    BARRIER_TIMEOUT_SECONDS = 30
    CSV_FLUSH_GRACE_SECONDS = 6
    RUN_MARKER = "LOCUST_INVALID"


class ScenarioState:
    def __init__(self):
        self.lock = Semaphore()
        self.confirm_start = Event()
        self.configuration_error = False
        self.ready_count = 0
        self.completed_count = 0
        self.success_count = 0
        self.stock_rejection_count = 0
        self.optimistic_conflict_count = 0
        self.unexpected_count = 0
        self.shutdown_scheduled = False

    def reset(self):
        with self.lock:
            self.confirm_start = Event()
            self.configuration_error = False
            self.ready_count = 0
            self.completed_count = 0
            self.success_count = 0
            self.stock_rejection_count = 0
            self.optimistic_conflict_count = 0
            self.unexpected_count = 0
            self.shutdown_scheduled = False

    def wait_for_confirm_wave(self):
        with self.lock:
            self.ready_count += 1
            if self.ready_count == CONCURRENT_USERS:
                self.confirm_start.set()

        if not self.confirm_start.wait(timeout=BARRIER_TIMEOUT_SECONDS):
            raise TimeoutError(
                f"결제 준비 완료 사용자가 {self.ready_count}/{CONCURRENT_USERS}명인 상태에서 "
                "동시 승인 장벽 대기시간을 초과했습니다."
            )

    def record_success(self):
        with self.lock:
            self.success_count += 1

    def record_stock_rejection(self):
        with self.lock:
            self.stock_rejection_count += 1

    def record_optimistic_conflict(self):
        with self.lock:
            self.optimistic_conflict_count += 1

    def record_unexpected(self):
        with self.lock:
            self.unexpected_count += 1

    def finish_user(self, environment):
        should_schedule_quit = False
        with self.lock:
            self.completed_count += 1
            if self.completed_count == CONCURRENT_USERS and not self.shutdown_scheduled:
                self.shutdown_scheduled = True
                should_schedule_quit = True
        if should_schedule_quit and environment.runner is not None:
            LOGGER.info(
                "모든 사용자가 완료되어 CSV 기록을 위해 %.1f초 후 종료합니다.",
                CSV_FLUSH_GRACE_SECONDS,
            )
            gevent.spawn_later(CSV_FLUSH_GRACE_SECONDS, environment.runner.quit)


STATE = ScenarioState()


@events.test_start.add_listener
def validate_test_configuration(environment, **kwargs):
    STATE.reset()
    configured_users = getattr(environment.parsed_options, "num_users", None)
    errors = list(CONFIGURATION_ERRORS)
    if configured_users is not None and configured_users != CONCURRENT_USERS:
        errors.append(
            f"Locust --users 값({configured_users})과 CONCURRENT_USERS({CONCURRENT_USERS})가 일치해야 합니다."
        )

    if errors:
        STATE.configuration_error = True
        for error in errors:
            LOGGER.error("설정 오류: %s", error)
        environment.process_exit_code = 2
        if environment.runner is not None:
            gevent.spawn_later(0, environment.runner.quit)
        return

    LOGGER.info(
        "재고 경합 시작: runMarker=%s, lockMode=%s, productId=%s, optionId=%s, users=%s, initialStock=%s",
        RUN_MARKER,
        EXPECTED_LOCK_MODE,
        TARGET_PRODUCT_ID,
        TARGET_OPTION_ID,
        CONCURRENT_USERS,
        INITIAL_STOCK,
    )


@events.test_stop.add_listener
def verify_test_result(environment, **kwargs):
    if STATE.configuration_error:
        return

    expected_successes = min(INITIAL_STOCK, CONCURRENT_USERS)
    expected_rejections = max(CONCURRENT_USERS - INITIAL_STOCK, 0)
    LOGGER.info(
        "재고 경합 결과: completed=%s, success=%s, stockRejected=%s, optimisticConflictRejected=%s, unexpected=%s",
        STATE.completed_count,
        STATE.success_count,
        STATE.stock_rejection_count,
        STATE.optimistic_conflict_count,
        STATE.unexpected_count,
    )

    if EXPECTED_LOCK_MODE == "pessimistic":
        valid_result = (
            STATE.completed_count == CONCURRENT_USERS
            and STATE.success_count == expected_successes
            and STATE.stock_rejection_count == expected_rejections
            and STATE.optimistic_conflict_count == 0
            and STATE.unexpected_count == 0
        )
    else:
        valid_result = (
            STATE.completed_count == CONCURRENT_USERS
            and STATE.success_count <= expected_successes
            and STATE.stock_rejection_count <= expected_rejections
            and STATE.success_count
            + STATE.stock_rejection_count
            + STATE.optimistic_conflict_count
            == CONCURRENT_USERS
            and STATE.unexpected_count == 0
        )
    if not valid_result:
        environment.process_exit_code = 1
        LOGGER.error(
            "재고 경합 결과가 기대값과 다릅니다: expectedSuccess=%s, expectedStockRejected=%s",
            expected_successes,
            expected_rejections,
        )


class GuestOrderPaymentUser(HttpUser):
    wait_time = between(0.01, 0.05)

    @task
    def create_and_confirm_order(self):
        if STATE.configuration_error:
            raise StopUser()

        try:
            order = self.create_order()
            if order is None:
                STATE.record_unexpected()
                return

            idempotency_key = uuid.uuid4().hex
            preparation = self.prepare_payment(order, idempotency_key)
            if preparation is None:
                STATE.record_unexpected()
                return

            STATE.wait_for_confirm_wave()
            self.confirm_payment(order, preparation, idempotency_key)
        except TimeoutError as error:
            STATE.record_unexpected()
            LOGGER.error("동시 승인 장벽 실패: %s", error)
        except Exception:
            STATE.record_unexpected()
            LOGGER.exception("주문·결제 부하 테스트 중 예상하지 못한 오류가 발생했습니다.")
        finally:
            STATE.finish_user(self.environment)
            raise StopUser()

    def create_order(self):
        suffix = uuid.uuid4().hex[:12]
        payload = {
            "cartItemIds": None,
            "directItems": [
                {
                    "productId": TARGET_PRODUCT_ID,
                    "optionId": TARGET_OPTION_ID,
                    "quantity": 1,
                }
            ],
            "orderer": {
                "name": RUN_MARKER,
                "phone": f"010{suffix}",
                "email": f"day12-{suffix}@example.com",
            },
            "receiver": {
                "name": "Day12 수령인",
                "phone": f"010{suffix}",
                "zipcode": "12345",
                "address": "서울시 동시성 테스트로 12",
                "addressDetail": suffix,
            },
            "deliveryMessage": "재고 동시성 부하 테스트",
        }
        with self.client.post(
            "/api/v1/orders",
            json=payload,
            name="01 POST /api/v1/orders",
            catch_response=True,
        ) as response:
            if response.status_code != 201:
                response.failure(f"주문 생성 상태코드 {response.status_code}: {response.text[:200]}")
                return None
            body = self.response_json(response, "주문 생성")
            if body is None or not body.get("orderNumber") or not body.get("guestOrderToken"):
                response.failure("주문 생성 응답에 주문번호 또는 비회원 주문 토큰이 없습니다.")
                return None
            response.success()
            return body

    def prepare_payment(self, order, idempotency_key):
        headers = {
            "Idempotency-Key": idempotency_key,
            "X-Guest-Order-Token": order["guestOrderToken"],
        }
        payload = {
            "orderNumber": order["orderNumber"],
            "paymentMethod": "CARD",
            "paymentProvider": "VIRTUAL",
        }
        with self.client.post(
            "/api/v1/payments/prepare",
            headers=headers,
            json=payload,
            name="02 POST /api/v1/payments/prepare",
            catch_response=True,
        ) as response:
            if response.status_code != 200:
                response.failure(f"결제 준비 상태코드 {response.status_code}: {response.text[:200]}")
                return None
            body = self.response_json(response, "결제 준비")
            if body is None or not body.get("orderId") or not body.get("amount"):
                response.failure("결제 준비 응답에 PG 주문번호 또는 금액이 없습니다.")
                return None
            response.success()
            return body

    def confirm_payment(self, order, preparation, idempotency_key):
        headers = {
            "Idempotency-Key": idempotency_key,
            "X-Guest-Order-Token": order["guestOrderToken"],
            "X-Virtual-Payment-Scenario": "SUCCESS",
        }
        payload = {
            "paymentKey": f"VIRTUAL-{uuid.uuid4().hex}",
            "orderId": preparation["orderId"],
            "amount": preparation["amount"],
        }
        with self.client.post(
            "/api/v1/payments/confirm",
            headers=headers,
            json=payload,
            name="03 POST /api/v1/payments/confirm",
            catch_response=True,
        ) as response:
            body = self.response_json(response, "결제 승인")
            if response.status_code == 200:
                if body is not None and body.get("paymentStatus") == "SUCCESS" and body.get("orderStatus") == "PAID":
                    STATE.record_success()
                    response.success()
                    return
                STATE.record_unexpected()
                response.failure(f"성공 결제 응답 상태가 올바르지 않습니다: {response.text[:200]}")
                return

            if response.status_code == 409 and body is not None and "재고가 부족" in body.get("message", ""):
                STATE.record_stock_rejection()
                response.success()
                return

            if (
                response.status_code == 409
                and body is not None
                and "재고 변경 충돌" in body.get("message", "")
            ):
                STATE.record_optimistic_conflict()
                response.success()
                return

            STATE.record_unexpected()
            response.failure(f"예상하지 못한 결제 승인 응답 {response.status_code}: {response.text[:200]}")

    @staticmethod
    def response_json(response, phase):
        try:
            return response.json()
        except ValueError:
            response.failure(f"{phase} 응답이 JSON이 아닙니다: {response.text[:200]}")
            return None
