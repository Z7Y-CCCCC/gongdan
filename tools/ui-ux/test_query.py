#!/usr/bin/env python3
"""stdlib-only meaningful regression tests for the relocatable read-only adapter."""
import sys

sys.dont_write_bytecode = True

import builtins
import hashlib
import importlib.util
import json
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

PACKAGE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("ui_ux_adapter_test_target", PACKAGE / "query.py")
adapter = importlib.util.module_from_spec(spec)
spec.loader.exec_module(adapter)


def signature(directory):
    return {path.relative_to(directory).as_posix(): hashlib.sha256(path.read_bytes()).hexdigest()
            for path in directory.rglob("*") if path.is_file()}


class QueryAdapterTest(unittest.TestCase):
    def test_real_ux_search_returns_focus_rules_and_codes_without_executing_them(self):
        response = adapter.query_references("keyboard focus visible", domain="ux")
        rows = response["result"]["results"]
        self.assertGreater(len(rows), 0)
        self.assertEqual(rows[0]["Issue"], "Focus States")
        self.assertIn("focus", rows[0]["Code Example Good"])
        self.assertFalse(response["policy"]["install_dependencies"])
        self.assertFalse(response["policy"]["apply_project_brand"])

    def test_chinese_keywords_are_locally_expanded_and_can_find_focus_guidelines(self):
        response = adapter.query_references("键盘 焦点")
        self.assertEqual(response["original_query"], "键盘 焦点")
        self.assertEqual(response["keyword_expansion"], ["keyboard", "focus"])
        self.assertEqual(response["result"]["results"][0]["Issue"], "Focus States")

    def test_vue34_results_warn_instead_of_silently_applying_vue35_reference(self):
        response = adapter.query_references("props destructure", stack="vue", target_version="3.4.34")
        self.assertTrue(response["warnings"])
        self.assertIn("3.4.34", " ".join(response["warnings"]))
        self.assertIn("3.5.x", " ".join(response["warnings"]))
        self.assertGreater(response["result"]["count"], 0)
        for row in response["result"]["results"]:
            self.assertEqual(row["compatibility"]["status"], "newer-reference")
            self.assertFalse(row["compatibility"]["automatic_application"])

    def test_missing_stack_version_explicitly_warns(self):
        response = adapter.query_references("watch cleanup", stack="vue")
        self.assertIn("未声明", " ".join(response["warnings"]))
        self.assertEqual(response["result"]["results"][0]["compatibility"]["status"], "target-version-required")

    def test_current_vue_scope_is_not_misrepresented_as_project_validation(self):
        response = adapter.query_references("watch cleanup", stack="vue", target_version="3.5.13")
        row = response["result"]["results"][0]
        self.assertEqual(row["compatibility"]["status"], "version-scope-match")
        self.assertIn("不等于代码已在本项目验证", row["compatibility"]["note"])

    def test_version_higher_than_a_pinned_minor_is_also_reviewed(self):
        response = adapter.query_references("watch cleanup", stack="vue", target_version="3.6.0")
        self.assertIn("资料范围", " ".join(response["warnings"]))
        self.assertEqual(response["result"]["results"][0]["compatibility"]["status"], "reference-range-mismatch")

    def test_other_stacks_and_domains_are_available_without_a_vue_or_green_requirement(self):
        react = adapter.query_references("server components", stack="react", target_version="19.2.1")
        colors = adapter.query_references("dashboard", domain="color")
        self.assertEqual(react["result"]["stack"], "react")
        self.assertGreater(react["result"]["count"], 0)
        self.assertEqual(colors["result"]["domain"], "color")
        self.assertGreater(colors["result"]["count"], 0)
        self.assertFalse(colors["policy"]["apply_project_brand"])

    def test_invalid_queries_limits_versions_and_selectors_fail_closed(self):
        for query, kwargs in [("", {}), (" " * 3, {}), ("x" * 2001, {}),
                              ("focus", {"max_results": 0}), ("focus", {"max_results": 21}),
                              ("focus", {"max_results": True}), ("focus", {"domain": "unknown"}),
                              ("focus", {"stack": "unknown"}), ("focus", {"domain": "ux", "stack": "vue"}),
                              ("focus", {"target_version": "3.4.34"}),
                              ("focus", {"stack": "vue", "target_version": "../../../3.4"})]:
            with self.subTest(kwargs=kwargs, query=query[:30]):
                with self.assertRaises(ValueError):
                    adapter.query_references(query, **kwargs)

    def test_no_network_no_file_writes_and_no_persistence_modules_imported(self):
        before = signature(PACKAGE)
        original_open = builtins.open

        def readonly_open(file, mode="r", *args, **kwargs):
            if any(flag in mode for flag in "wax+"):
                self.fail(f"Unexpected write: {file}")
            return original_open(file, mode, *args, **kwargs)

        with patch("builtins.open", side_effect=readonly_open), \
                patch("pathlib.Path.write_text", side_effect=AssertionError("write_text")), \
                patch("pathlib.Path.mkdir", side_effect=AssertionError("mkdir")), \
                patch("socket.create_connection", side_effect=AssertionError("network")) as socket_call, \
                patch("urllib.request.urlopen", side_effect=AssertionError("network")) as url_call:
            adapter.query_references("keyboard focus", domain="ux")
            adapter.query_references("watch cleanup", stack="vue", target_version="3.4.34")
            socket_call.assert_not_called()
            url_call.assert_not_called()
        self.assertEqual(before, signature(PACKAGE))
        self.assertNotIn("design_system", sys.modules)
        self.assertNotIn("preview", sys.modules)
        self.assertFalse(any(PACKAGE.rglob("__pycache__")))

    def test_license_and_every_vendored_file_match_the_pinned_manifest(self):
        provenance, payload = adapter.verify_vendor()
        self.assertEqual(provenance["revision"], "e8b065f0f3aaf2e680fd125f000e4f5e351ed108")
        self.assertEqual(payload["files"], 44)
        self.assertEqual(payload["bytes"], 2476356)
        license_text = (PACKAGE / "LICENSE").read_text(encoding="utf-8")
        self.assertIn("Copyright (c) 2024 Next Level Builder", license_text)
        self.assertIn("THE SOFTWARE IS PROVIDED", license_text)

    def test_git_checkout_does_not_normalize_audited_vendor_or_license_bytes(self):
        attributes = (PACKAGE / ".gitattributes").read_text(encoding="utf-8")
        self.assertIn("vendor/** -text", attributes)
        self.assertIn("LICENSE -text", attributes)

    def test_cli_is_relocatable_from_an_unrelated_cwd_and_does_not_create_files(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            relocated = directory / "portable-ui-ux"
            shutil.copytree(PACKAGE, relocated, ignore=shutil.ignore_patterns("__pycache__"))
            before = signature(directory)
            result = subprocess.run([sys.executable, "-B", str(relocated / "query.py"),
                                     "props destructure", "--stack", "vue", "--target-version", "3.4.34", "--json"],
                                    cwd=directory, capture_output=True, text=True, encoding="utf-8", timeout=30)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertTrue(json.loads(result.stdout)["warnings"])
            self.assertEqual(before, signature(directory))

    def test_cli_rejects_persistence_preview_and_install_options(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            before = signature(directory)
            for option in ("--persist", "--preview", "--design-system", "--install"):
                with self.subTest(option=option):
                    result = subprocess.run([sys.executable, "-B", str(PACKAGE / "query.py"), "dashboard", option],
                                            cwd=directory, capture_output=True, text=True, encoding="utf-8", timeout=30)
                    self.assertNotEqual(result.returncode, 0)
                    self.assertIn("unrecognized arguments", result.stderr)
            self.assertEqual(before, signature(directory))

    def test_modified_vendor_file_is_detected_before_the_import(self):
        with tempfile.TemporaryDirectory() as temporary:
            relocated = Path(temporary) / "portable-ui-ux"
            shutil.copytree(PACKAGE, relocated, ignore=shutil.ignore_patterns("__pycache__"))
            with (relocated / "vendor/scripts/core.py").open("a", encoding="utf-8") as handle:
                handle.write("\nraise RuntimeError('must not execute modified code')\n")
            result = subprocess.run([sys.executable, "-B", str(relocated / "query.py"), "focus"],
                                    capture_output=True, text=True, encoding="utf-8", timeout=30)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("Vendored reference changed", result.stderr)
            self.assertNotIn("must not execute modified code", result.stderr)


if __name__ == "__main__":
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8")
    unittest.main(verbosity=2)
