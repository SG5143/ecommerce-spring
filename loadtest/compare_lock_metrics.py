import argparse
import csv
from pathlib import Path


METRICS = {
    "Innodb_row_lock_current_waits",
    "Innodb_row_lock_time",
    "Innodb_row_lock_waits",
}


def parse_args():
    parser = argparse.ArgumentParser(description="MySQL InnoDB 행 잠금 지표의 실행 전후 차이를 계산합니다.")
    parser.add_argument("before", type=Path, help="실행 전 lock_metrics.sql TSV")
    parser.add_argument("after", type=Path, help="실행 후 lock_metrics.sql TSV")
    return parser.parse_args()


def read_metrics(path):
    if not path.is_file():
        raise SystemExit(f"지표 파일을 찾을 수 없습니다: {path}")
    with path.open(encoding="utf-8", newline="") as metrics_file:
        rows = csv.DictReader(metrics_file, delimiter="\t")
        values = {row["VARIABLE_NAME"]: int(row["VARIABLE_VALUE"]) for row in rows}
    missing = METRICS - values.keys()
    if missing:
        raise SystemExit(f"지표가 누락됐습니다: {', '.join(sorted(missing))}")
    return values


def main():
    args = parse_args()
    before = read_metrics(args.before)
    after = read_metrics(args.after)
    lock_time_delta = after["Innodb_row_lock_time"] - before["Innodb_row_lock_time"]
    lock_waits_delta = after["Innodb_row_lock_waits"] - before["Innodb_row_lock_waits"]
    average_wait = lock_time_delta / lock_waits_delta if lock_waits_delta > 0 else 0.0

    print(f"rowLockTimeMsDelta={lock_time_delta}")
    print(f"rowLockWaitsDelta={lock_waits_delta}")
    print(f"rowLockAverageMs={average_wait:.2f}")
    print(f"currentWaitsAfter={after['Innodb_row_lock_current_waits']}")


if __name__ == "__main__":
    main()
