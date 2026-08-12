import argparse
import csv
import os
import subprocess
import sys
from pathlib import Path


USERS = (20, 50, 100, 200)
REPETITIONS = 3
REPO_ROOT = Path(__file__).resolve().parent.parent
LOADTEST_ROOT = REPO_ROOT / "loadtest"
RESULTS_ROOT = LOADTEST_ROOT / "results"


def parse_args():
    parser = argparse.ArgumentParser(description="Day 12 재고 잠금 부하 테스트 매트릭스를 실행합니다.")
    parser.add_argument("--lock-mode", choices=("pessimistic", "optimistic"), required=True)
    parser.add_argument("--database", required=True)
    parser.add_argument("--host-url", required=True)
    parser.add_argument("--product-id", type=int, required=True)
    parser.add_argument("--option-id", type=int, required=True)
    parser.add_argument("--mysql-host", default="127.0.0.1")
    parser.add_argument("--mysql-user", default="root")
    parser.add_argument("--mysql-password", default="root")
    return parser.parse_args()


def mysql_command(args, *extra):
    return [
        "mysql",
        "-h",
        args.mysql_host,
        "-u",
        args.mysql_user,
        *extra,
        args.database,
    ]


def command_environment(args):
    environment = os.environ.copy()
    environment["MYSQL_PWD"] = args.mysql_password
    return environment


def run_mysql_sql(args, sql, capture_output=False):
    return subprocess.run(
        mysql_command(args, "--batch"),
        cwd=REPO_ROOT,
        env=command_environment(args),
        input=sql,
        text=True,
        capture_output=capture_output,
        check=True,
    )


def source_sql(args, variables, filename, capture_output=False):
    assignments = " ".join(f"SET @{name}={value};" for name, value in variables.items())
    sql = f"{assignments} SOURCE loadtest/sql/{filename};"
    return run_mysql_sql(args, sql, capture_output=capture_output)


def current_stock(args):
    result = run_mysql_sql(
        args,
        f"SELECT stock_quantity FROM product_option WHERE id={args.option_id};",
        capture_output=True,
    )
    rows = result.stdout.strip().splitlines()
    if len(rows) != 2:
        raise RuntimeError(f"대상 옵션 재고를 확인할 수 없습니다: {result.stdout}")
    return int(rows[1])


def snapshot_metrics(args, path):
    sql = (LOADTEST_ROOT / "sql" / "lock_metrics.sql").read_text(encoding="utf-8")
    result = run_mysql_sql(args, sql, capture_output=True)
    path.write_text(result.stdout, encoding="utf-8")


def verify_database(args, marker, initial_stock):
    result = source_sql(
        args,
        {
            "target_option_id": args.option_id,
            "initial_stock": initial_stock,
            "run_marker": f"'{marker}'",
        },
        "verify.sql",
        capture_output=True,
    )
    rows = list(csv.DictReader(result.stdout.splitlines(), delimiter="\t"))
    if len(rows) != 1:
        raise RuntimeError(f"DB 검증 결과가 한 행이 아닙니다: {result.stdout}")
    row = rows[0]
    if row["stock_conservation_passed"] != "1" or row["no_oversell_passed"] != "1":
        raise RuntimeError(f"재고 불변식 검증에 실패했습니다: {row}")
    return row


def run_locust(args, run_id, users, initial_stock, result_prefix):
    environment = os.environ.copy()
    environment.update(
        {
            "EXPECTED_LOCK_MODE": args.lock_mode,
            "RUN_ID": run_id,
            "TARGET_PRODUCT_ID": str(args.product_id),
            "TARGET_OPTION_ID": str(args.option_id),
            "CONCURRENT_USERS": str(users),
            "INITIAL_STOCK": str(initial_stock),
            "BARRIER_TIMEOUT_SECONDS": "30",
            "CSV_FLUSH_GRACE_SECONDS": "6",
        }
    )
    subprocess.run(
        [
            str(REPO_ROOT / ".venv-locust" / "bin" / "locust"),
            "-f",
            str(LOADTEST_ROOT / "locustfile.py"),
            "--headless",
            "--host",
            args.host_url,
            "--users",
            str(users),
            "--spawn-rate",
            str(users),
            "--run-time",
            "60s",
            "--csv",
            str(result_prefix),
            "--only-summary",
        ],
        cwd=REPO_ROOT,
        env=environment,
        check=True,
    )


def verify_csv(result_prefix, users):
    subprocess.run(
        [
            sys.executable,
            str(LOADTEST_ROOT / "verify_csv.py"),
            f"{result_prefix}_stats.csv",
            "--users",
            str(users),
        ],
        cwd=REPO_ROOT,
        check=True,
    )


def compare_metrics(before_path, after_path, output_path):
    result = subprocess.run(
        [
            sys.executable,
            str(LOADTEST_ROOT / "compare_lock_metrics.py"),
            str(before_path),
            str(after_path),
        ],
        cwd=REPO_ROOT,
        text=True,
        capture_output=True,
        check=True,
    )
    output_path.write_text(result.stdout, encoding="utf-8")


def run_case(args, scenario, users, initial_stock, repetition, restore_stock):
    run_id = f"{scenario}-u{users}-r{repetition}"
    marker = f"LOCUST_DAY12_{args.lock_mode}_{run_id}"
    result_prefix = RESULTS_ROOT / f"{args.lock_mode}-{run_id}"
    before_path = Path(f"{result_prefix}-before.tsv")
    after_path = Path(f"{result_prefix}-after.tsv")
    print(f"START: mode={args.lock_mode}, scenario={scenario}, users={users}, repetition={repetition}")

    source_sql(
        args,
        {
            "target_option_id": args.option_id,
            "initial_stock": initial_stock,
            "run_marker": f"'{marker}'",
        },
        "prepare.sql",
    )
    try:
        snapshot_metrics(args, before_path)
        run_locust(args, run_id, users, initial_stock, result_prefix)
        verify_csv(result_prefix, users)
        verification = verify_database(args, marker, initial_stock)
        snapshot_metrics(args, after_path)
        compare_metrics(before_path, after_path, Path(f"{result_prefix}-lock-metrics.txt"))
        print(
            "PASS: "
            f"mode={args.lock_mode}, scenario={scenario}, users={users}, "
            f"success={verification['successful_payments']}, finalStock={verification['final_stock']}"
        )
    finally:
        source_sql(
            args,
            {
                "target_option_id": args.option_id,
                "run_marker": f"'{marker}'",
                "restore_stock": restore_stock,
            },
            "cleanup.sql",
        )


def main():
    args = parse_args()
    if args.product_id <= 0 or args.option_id <= 0:
        raise SystemExit("상품과 옵션 ID는 0보다 커야 합니다.")
    RESULTS_ROOT.mkdir(parents=True, exist_ok=True)
    restore_stock = current_stock(args)

    for scenario, stock_for_users in (
        ("warmup-limited", lambda users: 5),
        ("warmup-full", lambda users: users),
    ):
        run_case(args, scenario, 20, stock_for_users(20), 0, restore_stock)

    for scenario, stock_for_users in (
        ("limited", lambda users: 5),
        ("full", lambda users: users),
    ):
        for users in USERS:
            for repetition in range(1, REPETITIONS + 1):
                run_case(args, scenario, users, stock_for_users(users), repetition, restore_stock)


if __name__ == "__main__":
    main()
