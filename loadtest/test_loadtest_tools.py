import subprocess
import sys
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch


LOADTEST_ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(LOADTEST_ROOT))

import run_matrix
import summarize_results


class RunMatrixTest(unittest.TestCase):

    def test_mysql_command_includes_configured_port(self):
        args = SimpleNamespace(
            mysql_host="127.0.0.1",
            mysql_port=13307,
            mysql_user="loadtest",
            database="mingler_day15",
        )

        command = run_matrix.mysql_command(args, "--batch")

        self.assertEqual(
            command,
            [
                "mysql",
                "-h",
                "127.0.0.1",
                "-P",
                "13307",
                "-u",
                "loadtest",
                "--batch",
                "mingler_day15",
            ],
        )

    def test_pessimistic_result_requires_exact_success_count(self):
        args = SimpleNamespace(option_id=1, lock_mode="pessimistic")
        output = (
            "run_marker\tinitial_stock\tfinal_stock\tcreated_orders\t"
            "successful_payments\tstock_rejected_payments\t"
            "stock_conservation_passed\tno_oversell_passed\n"
            "RUN\t5\t1\t20\t4\t16\t1\t1\n"
        )
        result = subprocess.CompletedProcess([], 0, stdout=output)

        with patch.object(run_matrix, "source_sql", return_value=result):
            with self.assertRaisesRegex(RuntimeError, "성공 결제 수가 다릅니다"):
                run_matrix.verify_database(args, "RUN", 20, 5)

    def test_optimistic_result_keeps_invariant_only_validation(self):
        args = SimpleNamespace(option_id=1, lock_mode="optimistic")
        output = (
            "run_marker\tinitial_stock\tfinal_stock\tcreated_orders\t"
            "successful_payments\tstock_rejected_payments\t"
            "stock_conservation_passed\tno_oversell_passed\n"
            "RUN\t20\t2\t20\t18\t2\t1\t1\n"
        )
        result = subprocess.CompletedProcess([], 0, stdout=output)

        with patch.object(run_matrix, "source_sql", return_value=result):
            row = run_matrix.verify_database(args, "RUN", 20, 20)

        self.assertEqual(row["successful_payments"], "18")

    def test_run_label_is_passed_to_locust(self):
        args = SimpleNamespace(
            lock_mode="pessimistic",
            run_label="D15L",
            product_id=1,
            option_id=1,
            host_url="http://127.0.0.1:18080",
        )

        with patch.object(run_matrix.subprocess, "run") as run:
            run_matrix.run_locust(args, "limited-u20-r1", 20, 5, Path("results/run"))

        environment = run.call_args.kwargs["env"]
        self.assertEqual(environment["RUN_LABEL"], "D15L")


class SummarizeResultsTest(unittest.TestCase):

    def test_percentage_change_calculates_increase_and_decrease(self):
        self.assertEqual(summarize_results.percentage_change(120, 100), 20)
        self.assertEqual(summarize_results.percentage_change(80, 100), -20)

    def test_percentage_change_rejects_zero_baseline(self):
        with self.assertRaisesRegex(RuntimeError, "기준값이 0"):
            summarize_results.percentage_change(1, 0)

    def test_regression_warning_includes_threshold_boundary(self):
        self.assertTrue(summarize_results.has_regression_warning(20, 0, 20))
        self.assertTrue(summarize_results.has_regression_warning(0, -20, 20))
        self.assertFalse(summarize_results.has_regression_warning(19.9, -19.9, 20))


if __name__ == "__main__":
    unittest.main()
