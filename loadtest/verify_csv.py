import argparse
import csv
from pathlib import Path


ENDPOINTS = {
    "01 POST /api/v1/orders",
    "02 POST /api/v1/payments/prepare",
    "03 POST /api/v1/payments/confirm",
}


def parse_args():
    parser = argparse.ArgumentParser(description="Locust Day 12 CSV 요청 수를 검증합니다.")
    parser.add_argument("stats_csv", type=Path, help="Locust *_stats.csv 파일")
    parser.add_argument("--users", type=int, required=True, help="기대한 동시 사용자 수")
    return parser.parse_args()


def read_rows(path):
    with path.open(encoding="utf-8", newline="") as csv_file:
        return list(csv.DictReader(csv_file))


def request_count(row):
    return int(row["Request Count"])


def failure_count(row):
    return int(row["Failure Count"])


def main():
    args = parse_args()
    if args.users <= 0:
        raise SystemExit("--users는 0보다 커야 합니다.")
    if not args.stats_csv.is_file():
        raise SystemExit(f"CSV 파일을 찾을 수 없습니다: {args.stats_csv}")

    rows = read_rows(args.stats_csv)
    endpoint_rows = {row["Name"]: row for row in rows if row["Name"] in ENDPOINTS}
    errors = []
    for endpoint in sorted(ENDPOINTS):
        row = endpoint_rows.get(endpoint)
        if row is None:
            errors.append(f"엔드포인트 행이 없습니다: {endpoint}")
            continue
        if request_count(row) != args.users:
            errors.append(
                f"{endpoint} 요청 수가 다릅니다: expected={args.users}, actual={request_count(row)}"
            )
        if failure_count(row) != 0:
            errors.append(f"{endpoint} HTTP 실패가 있습니다: {failure_count(row)}")

    aggregated = next((row for row in rows if row["Name"] == "Aggregated"), None)
    expected_total = args.users * len(ENDPOINTS)
    if aggregated is None:
        errors.append("Aggregated 행이 없습니다.")
    elif request_count(aggregated) != expected_total:
        errors.append(
            f"전체 요청 수가 다릅니다: expected={expected_total}, actual={request_count(aggregated)}"
        )

    if errors:
        for error in errors:
            print(f"FAIL: {error}")
        raise SystemExit(1)

    print(
        f"PASS: endpoints={len(ENDPOINTS)}, users={args.users}, totalRequests={expected_total}, failures=0"
    )


if __name__ == "__main__":
    main()
