import argparse
import csv
from pathlib import Path
from statistics import median


USERS = (20, 50, 100, 200)
SCENARIOS = ("limited", "full")
CONFIRM_ENDPOINT = "03 POST /api/v1/payments/confirm"


def parse_args():
    parser = argparse.ArgumentParser(description="Locust 3회 결과의 중앙값 표를 생성합니다.")
    parser.add_argument("--results", type=Path, default=Path("loadtest/results"))
    parser.add_argument(
        "--lock-modes",
        nargs="+",
        choices=("pessimistic", "optimistic"),
        default=("pessimistic", "optimistic"),
    )
    parser.add_argument("--users", type=int, nargs="+", default=USERS)
    parser.add_argument("--baseline-results", type=Path)
    parser.add_argument("--warning-threshold-percent", type=float, default=20.0)
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


def percentage_change(current, baseline):
    if baseline == 0:
        raise RuntimeError("기준값이 0이라 증감률을 계산할 수 없습니다.")
    return (current - baseline) / baseline * 100


def has_regression_warning(p95_change, rps_change, warning_threshold):
    return p95_change >= warning_threshold or rps_change <= -warning_threshold


def print_table(results_root, scenario, modes, users_values):
    print(f"### {scenario}")
    print("| 락 | 사용자 | 평균 | p50 | p95 | p99 | 최대 | 승인 RPS | 행 잠금 시간 | 행 잠금 대기 | 평균 대기 |")
    print("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |")
    for mode in modes:
        for users in users_values:
            values = summarize(results_root, mode, scenario, users)
            print(
                f"| {mode} | {users} | {values['average']:.1f}ms | {values['p50']:.0f}ms | "
                f"{values['p95']:.0f}ms | {values['p99']:.0f}ms | {values['maximum']:.1f}ms | "
                f"{values['rps']:.2f} | {values['lock_time']:.0f}ms | {values['lock_waits']:.0f} | "
                f"{values['lock_average']:.2f}ms |"
            )


def print_comparison_table(results_root, baseline_root, scenario, modes, users_values, warning_threshold):
    print(f"### {scenario} Day 12 대비")
    print("| 락 | 사용자 | p95 증감 | RPS 증감 | 판정 |")
    print("| --- | ---: | ---: | ---: | --- |")
    for mode in modes:
        for users in users_values:
            current = summarize(results_root, mode, scenario, users)
            baseline = summarize(baseline_root, mode, scenario, users)
            p95_change = percentage_change(current["p95"], baseline["p95"])
            rps_change = percentage_change(current["rps"], baseline["rps"])
            warning = has_regression_warning(p95_change, rps_change, warning_threshold)
            verdict = "조사 필요" if warning else "허용 범위"
            print(
                f"| {mode} | {users} | {p95_change:+.1f}% | {rps_change:+.1f}% | {verdict} |"
            )


def main():
    args = parse_args()
    if not args.results.is_dir():
        raise SystemExit(f"결과 디렉터리를 찾을 수 없습니다: {args.results}")
    if not args.users or any(users <= 0 for users in args.users):
        raise SystemExit("--users는 0보다 큰 값을 하나 이상 지정해야 합니다.")
    if len(set(args.users)) != len(args.users):
        raise SystemExit("--users에 중복된 값을 지정할 수 없습니다.")
    if args.warning_threshold_percent < 0:
        raise SystemExit("--warning-threshold-percent는 0 이상이어야 합니다.")
    if args.baseline_results is not None and not args.baseline_results.is_dir():
        raise SystemExit(f"기준 결과 디렉터리를 찾을 수 없습니다: {args.baseline_results}")
    for scenario in SCENARIOS:
        print_table(args.results, scenario, args.lock_modes, args.users)
    if args.baseline_results is not None:
        for scenario in SCENARIOS:
            print_comparison_table(
                args.results,
                args.baseline_results,
                scenario,
                args.lock_modes,
                args.users,
                args.warning_threshold_percent,
            )


if __name__ == "__main__":
    main()
