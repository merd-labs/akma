#!/usr/bin/env python3
"""Synthetic transport checks. Never invoke real ADB or create device evidence."""
import csv
import io
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parent
SERIAL = "SYNTHETIC_SERIAL"
PRIVATE = "PRIVATE_SENTINEL_NEVER_SAVE"
FAKE_ADB = r'''
import json, os, sys, time
args = sys.argv[1:]
with open(os.environ["BENCH_TEST_TRACE"], "a") as trace:
    trace.write(json.dumps(args) + "\n")
if len(args) < 3 or args[:2] != ["-s", "SYNTHETIC_SERIAL"]:
    sys.exit(91)
command = " ".join(args[2:])
mode = os.environ.get("BENCH_TEST_MODE", "normal")
if mode == "denied":
    print("PRIVATE_SENTINEL_NEVER_SAVE SYNTHETIC_SERIAL", file=sys.stderr)
    sys.exit(1)
if mode == "timeout" and command == "shell dumpsys thermalservice":
    time.sleep(30)
if mode == "thermal-denied" and command == "shell dumpsys thermalservice":
    print("PRIVATE_SENTINEL_NEVER_SAVE", file=sys.stderr)
    sys.exit(1)
data = {
    "get-state": mode if mode in ("offline", "unauthorized") else "device",
    "shell getprop ro.kernel.qemu": "1" if mode == "qemu" else "",
    "shell getprop ro.boot.qemu": "1" if mode == "boot-qemu" else "",
    "shell getprop ro.build.version.sdk": "30",
    "shell getprop ro.build.version.release": "11",
    "shell getprop ro.product.cpu.abi": "arm64-v8a",
    "shell getprop ro.product.model": "TECNO LE7 SYNTHETIC_SERIAL",
    "shell cat /proc/meminfo": "MemTotal: 6000000 kB\nMemAvailable: 2500000 kB\nPRIVATE_SENTINEL_NEVER_SAVE",
    "shell df -k /data": "Filesystem 1K-blocks Used Available Use% Mounted on\n/dev/block/test 100000000 20000000 80000000 20% /data\nPRIVATE_SENTINEL_NEVER_SAVE",
    "shell dumpsys battery": "temperature: 315\nPRIVATE_SENTINEL_NEVER_SAVE",
    "shell dumpsys thermalservice": "Thermal Status: 0\nPRIVATE_SENTINEL_NEVER_SAVE",
    "shell dumpsys meminfo ph.merd.akma": " TOTAL 100000 0 0\n TOTAL PSS: 123456 TOTAL RSS: 200000\nPRIVATE_SENTINEL_NEVER_SAVE",
    "shell dumpsys meminfo ph.merd.test": " TOTAL PSS: 654321\nPRIVATE_SENTINEL_NEVER_SAVE",
}
if mode == "missing":
    data["shell cat /proc/meminfo"] = "MemTotal: 6000000 kB\nMemAvailable: PRIVATE_SENTINEL_NEVER_SAVE kB"
    data["shell dumpsys battery"] = "temperature: PRIVATE_SENTINEL_NEVER_SAVE"
    data["shell dumpsys meminfo ph.merd.akma"] = "No process found for: ph.merd.akma"
if mode == "legacy":
    data["shell dumpsys meminfo ph.merd.akma"] = " TOTAL 123456 0 0\nPRIVATE_SENTINEL_NEVER_SAVE"
if command not in data:
    sys.exit(92)
print(data[command])
'''


class CollectorChecks(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="akma-bench-test-")
        self.directory = Path(self.temp.name)
        fake = self.directory / "adb"
        fake.write_text(f"#!{sys.executable}\n" + FAKE_ADB)
        fake.chmod(0o700)
        self.trace = self.directory / "trace.jsonl"
        self.output = self.directory / "snapshot.tsv"
        self.env = dict(os.environ, PATH=str(self.directory) + os.pathsep + os.environ["PATH"],
                        BENCH_TEST_TRACE=str(self.trace))
        self.hosts = ["bash"]
        if shutil.which("pwsh"):
            self.hosts.append("pwsh")

    def tearDown(self):
        self.temp.cleanup()

    def command(self, host, *, serial=SERIAL, slot=True, package="ph.merd.akma", help_only=False):
        if host == "bash":
            prefix = ["bash", str(ROOT / "collect.sh")]
            if help_only:
                return prefix + ["--help"]
            args = ["--serial", serial, "--device", "pova2", "--phase", "warm-1-after",
                    "--output", str(self.output), "--package", package]
            return prefix + args + (["--slot-confirmed"] if slot else [])
        prefix = ["pwsh", "-NoProfile", "-File", str(ROOT / "collect.ps1")]
        if help_only:
            return prefix + ["-Help"]
        args = ["-Serial", serial, "-Device", "pova2", "-Phase", "warm-1-after",
                "-Output", str(self.output), "-Package", package]
        return prefix + args + (["-SlotConfirmed"] if slot else [])

    def run_collector(self, host, mode="normal", **kwargs):
        self.trace.unlink(missing_ok=True)
        env = dict(self.env, BENCH_TEST_MODE=mode)
        result = subprocess.run(self.command(host, **kwargs), env=env, capture_output=True,
                                text=True, timeout=45)
        self.assertNotIn(SERIAL, result.stdout + result.stderr)
        self.assertNotIn(PRIVATE, result.stdout + result.stderr)
        return result

    def rows(self):
        content = self.output.read_text(encoding="utf-8")
        self.assertNotIn(SERIAL, content)
        self.assertNotIn(PRIVATE, content)
        self.assertNotIn("\r", self.output.read_bytes().decode())
        rows = list(csv.DictReader(io.StringIO(content), delimiter="\t"))
        self.assertEqual(len(rows), 10)
        self.assertTrue(all(row["device_alias"] == "pova2" for row in rows))
        self.assertTrue(all(row["phase"] == "warm-1-after" for row in rows))
        self.assertTrue(all(row["timestamp_utc"].endswith("Z") for row in rows))
        return {row["metric"]: row for row in rows}

    def test_help_has_no_device_calls(self):
        for host in self.hosts:
            with self.subTest(host=host):
                result = self.run_collector(host, help_only=True)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertIn("Usage:", result.stdout)
                self.assertFalse(self.trace.exists())

    def test_normal_and_legacy_data(self):
        expected = {"android_api": "30", "android_release": "11", "abi": "arm64-v8a",
                    "model": "TECNO LE7 [REDACTED]",
                    "mem_total": "6000000", "mem_available": "2500000",
                    "data_available": "80000000", "battery_temperature": "315",
                    "thermal_status": "0", "app_total_pss": "123456"}
        for host in self.hosts:
            for mode in ("normal", "legacy"):
                with self.subTest(host=host, mode=mode):
                    self.output.unlink(missing_ok=True)
                    result = self.run_collector(host, mode)
                    self.assertEqual(result.returncode, 0, result.stderr)
                    rows = self.rows()
                    for metric, value in expected.items():
                        self.assertEqual(rows[metric]["value"], value)
                        self.assertEqual(rows[metric]["status"], "ok")
                    commands = [json.loads(line) for line in self.trace.read_text().splitlines()]
                    self.assertTrue(all(args[:2] == ["-s", SERIAL] for args in commands))
                    self.assertEqual(len(commands), 12)

    def test_validation_before_device_calls(self):
        for host in self.hosts:
            for options in ({"serial": ""}, {"serial": "bad;command"},
                            {"serial": "emulator-5554"}, {"slot": False},
                            {"package": "ph.merd.akma;bad"}):
                with self.subTest(host=host, options=options):
                    result = self.run_collector(host, **options)
                    self.assertNotEqual(result.returncode, 0)
                    self.assertFalse(self.trace.exists())
                    self.assertFalse(self.output.exists())

    def test_denied_and_emulator_property(self):
        for host in self.hosts:
            for mode in ("denied", "offline", "unauthorized", "qemu", "boot-qemu"):
                with self.subTest(host=host, mode=mode):
                    result = self.run_collector(host, mode)
                    self.assertNotEqual(result.returncode, 0)
                    self.assertFalse(self.output.exists())

    def test_missing_values_and_service_failure(self):
        for host in self.hosts:
            self.output.unlink(missing_ok=True)
            with self.subTest(host=host, mode="missing"):
                result = self.run_collector(host, "missing")
                self.assertEqual(result.returncode, 0, result.stderr)
                rows = self.rows()
                for metric in ("mem_available", "battery_temperature", "app_total_pss"):
                    self.assertEqual(rows[metric]["value"], "")
                    self.assertEqual(rows[metric]["status"], "unavailable")
            self.output.unlink()
            with self.subTest(host=host, mode="thermal-denied"):
                result = self.run_collector(host, "thermal-denied")
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertEqual(self.rows()["thermal_status"]["status"], "query_failed")

    def test_timeout_is_partial_evidence(self):
        for host in self.hosts:
            self.output.unlink(missing_ok=True)
            with self.subTest(host=host):
                result = self.run_collector(host, "timeout")
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertEqual(self.rows()["thermal_status"]["status"], "timeout")

    def test_no_overwrite(self):
        for host in self.hosts:
            with self.subTest(host=host):
                self.output.write_text("KEEP EXISTING FILE\n")
                result = self.run_collector(host)
                self.assertNotEqual(result.returncode, 0)
                self.assertFalse(self.trace.exists())
                self.assertEqual(self.output.read_text(), "KEEP EXISTING FILE\n")

    def test_package_override(self):
        for host in self.hosts:
            self.output.unlink(missing_ok=True)
            with self.subTest(host=host):
                result = self.run_collector(host, package="ph.merd.test")
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertEqual(self.rows()["app_total_pss"]["value"], "654321")


if __name__ == "__main__":
    if not shutil.which("pwsh"):
        print("SKIP: PowerShell host unavailable; only Bash transport checks run.", file=sys.stderr)
    unittest.main(verbosity=2)
