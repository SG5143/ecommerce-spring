import argparse
import csv
from pathlib import Path
from statistics import median


USERS = (20, 50, 100, 200)
SCENARIOS = ("limited", "full")
CONFIRM_ENDPOINT = "03 POST /api/v1/payments/confirm"


def parse_args():
    parser = argparse.ArgumentParser(description="Day 12 Locust 3회 결과의 중앙값 표를 생성합니다.")
    parser.add_argument("--results", type=Path, default=Path("loadtest/results"))
    return parser.parse_args()


def read_confirm_row(path):
    with path.open(encoding="utf-8", newline="") as csv_file:
        rows = csv.DictReader(csv_file)
        row = next((item for item in rows if item["Name"] == CONFIRM_ENDPOINT), None)
    if row is None:
        raise RuntimeError(f"결제 승인 행이 없습니다: {path}")
    return row


def read_lock_metrics(path):
    values = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if "=" in line:
            name, value = line.split("=", 1)
            values[name] = float(value)
    return values


def metric_median(rows, name):
    return median(float(row[name]) for row in rows)


def summarize(results_root, mode, scenario, users):
    stats_paths = sorted(results_root.glob(f"{mode}-{scenario}-u{users}-r[123]_stats.csv"))
    lock_paths = sorted(results_root.glob(f"{mode}-{scenario}-u{users}-r[123]-lock-metrics.txt"))
    if len(stats_paths) != 3 or len(lock_paths) != 3:
        raise RuntimeError(
            f"3회 결과가 필요합니다: mode={mode}, scenario={scenario}, users={users}, "
            f"stats={len(stats_paths)}, lockMetrics={len(lock_paths)}"
        )
    rows = [read_confirm_row(path) for path in stats_paths]
    lock_rows = [read_lock_metrics(path) for path in lock_paths]
    return {
        "average": metric_median(rows, "Average Response Time"),
        "p50": metric_median(rows, "50%"),
        "p95": metric_median(rows, "95%"),
        "p99": metric_median(rows, "99%"),
        "maximum": metric_median(rows, "Max Response Time"),
        "rps": metric_median(rows, "Requests/s"),
        "lock_time": median(row["rowLockTimeMsDelta"] for row in lock_rows),
        "lock_waits": median(row["rowLockWaitsDelta"] for row in lock_rows),
        "lock_average": median(row["rowLockAverageMs"] for row in lock_rows),
    }


def print_table(results_root, scenario):
    print(f"### {scenario}")
    print("| 락 | 사용자 | 평균 | p50 | p95 | p99 | 최대 | 승인 RPS | 행 잠금 시간 | 행 잠금 대기 | 평균 대기 |")
    print("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |")
    for mode in ("pessimistic", "optimistic"):
        for users in USERS:
            values = summarize(results_root, mode, scenario, users)
            print(
                f"| {mode} | {users} | {values['average']:.1f}ms | {values['p50']:.0f}ms | "
                f"{values['p95']:.0f}ms | {values['p99']:.0f}ms | {values['maximum']:.1f}ms | "
                f"{values['rps']:.2f} | {values['lock_time']:.0f}ms | {values['lock_waits']:.0f} | "
                f"{values['lock_average']:.2f}ms |"
            )


def main():
    args = parse_args()
    if not args.results.is_dir():
        raise SystemExit(f"결과 디렉터리를 찾을 수 없습니다: {args.results}")
    for scenario in SCENARIOS:
        print_table(args.results, scenario)


if __name__ == "__main__":
    main()
