#!/usr/bin/env python3
"""Relocatable, read-only adapter over pinned UI/UX Pro Max CSV references.

Only the audited core search module is imported. Upstream persistence and preview
entry points are not exposed. Results are reference material, not instructions to
install packages, change a project's stack, or execute the included examples.
"""
import sys

sys.dont_write_bytecode = True

import argparse
import hashlib
import importlib.util
import json
import re
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent
VENDOR_DIR = BASE_DIR / "vendor"
_ENGINE = None

KEYWORD_ALIASES = {
    "键盘": "keyboard", "焦点": "focus", "表单": "form", "标签": "label",
    "按钮": "button", "错误": "error", "重试": "retry", "触摸": "touch",
    "手机": "mobile", "动画": "animation", "动效": "motion", "表格": "table",
    "分页": "pagination", "状态": "state", "颜色": "color", "图标": "icon",
    "无障碍": "accessibility", "属性解构": "props destructure", "监听": "watch",
}
POLICY = {
    "reference_only": True, "persistence": False, "network": False,
    "install_dependencies": False, "change_project_stack": False,
    "apply_project_brand": False,
}


def verify_vendor():
    """Check all licensed payload files before importing upstream executable code."""
    provenance = json.loads((BASE_DIR / "UPSTREAM.json").read_text(encoding="utf-8"))
    total_bytes = 0
    for entry in provenance["files"]:
        path = (BASE_DIR / entry["path"]).resolve()
        if not path.is_relative_to(BASE_DIR):
            raise ValueError("Invalid path in UPSTREAM.json")
        data = path.read_bytes()
        if hashlib.sha256(data).hexdigest() != entry["sha256"]:
            raise ValueError(f"Vendored reference changed: {entry['path']}")
        total_bytes += len(data)
    return provenance, {"files": len(provenance["files"]), "bytes": total_bytes}


def load_engine():
    global _ENGINE
    provenance, payload = verify_vendor()
    if _ENGINE is None:
        spec = importlib.util.spec_from_file_location(
            "_ethan_ui_ux_reference_core", VENDOR_DIR / "scripts" / "core.py"
        )
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        _ENGINE = module
    return _ENGINE, provenance, payload


def _version(value):
    if not isinstance(value, str) or len(value) > 40 or not re.fullmatch(
        r"\d+(?:\.\d+){0,2}(?:-[A-Za-z0-9.-]+)?", value
    ):
        raise ValueError("target-version must be a numeric version, e.g. 3.4.34")
    return tuple(int(part) for part in value.split("-", 1)[0].split("."))


def _compatibility(row, stack, target_version, engine):
    scope = row.get("Applies To") or engine.STACK_CURRENT_APPLICABILITY.get(stack, "unverified")
    result = {"target_version": target_version, "reference_scope": scope,
              "status": "review-required", "automatic_application": False}
    if not target_version:
        result.update(status="target-version-required", warning=
                      "未声明目标版本；不能把栈检索结果当作项目兼容承诺。")
        return result
    target = _version(target_version)
    match = re.search(r"(?<!\d)(\d+(?:\.\d+){0,2})(?:\.x)?", scope, re.I)
    if not match:
        result["warning"] = "资料没有可判断的版本范围；需对照项目依赖与官方文档复核。"
        return result
    reference = _version(match.group(1))
    comparable = target + (0,) * max(0, len(reference) - len(target))
    if comparable[:len(reference)] < reference:
        result.update(status="newer-reference", warning=
                      f"目标 {stack} {target_version} 早于资料范围 {scope}；不可直接套用，先核对官方文档与现有实现。")
    elif ".x" in scope.lower() and target[:len(reference)] != reference:
        result.update(status="reference-range-mismatch", warning=
                      f"目标版本不在资料范围 {scope}；数字更高也不代表已验证。")
    elif "-" in target_version:
        result["warning"] = "目标是预发布版本；需另行核对兼容性。"
    else:
        result.update(status="version-scope-match", note=
                      "版本数字符合资料标注，不等于代码已在本项目验证；仍需测试，不自动升级或增加依赖。")
    return result


def query_references(query, domain=None, stack=None, target_version=None, max_results=3):
    if not isinstance(query, str) or not query.strip():
        raise ValueError("Query must not be empty")
    if len(query) > 2000:
        raise ValueError("Query is too long (maximum 2000 characters)")
    if type(max_results) is not int or not 1 <= max_results <= 20:
        raise ValueError("max-results must be between 1 and 20")
    if domain and stack:
        raise ValueError("Choose a domain or a stack, not both")
    if target_version and not stack:
        raise ValueError("target-version requires --stack")
    if target_version:
        _version(target_version)
    engine, provenance, payload = load_engine()
    if stack and stack not in engine.AVAILABLE_STACKS:
        raise ValueError(f"Unknown stack: {stack}")
    domain = domain or (None if stack else "ux")
    if domain and domain not in engine.CSV_CONFIG:
        raise ValueError(f"Unknown domain: {domain}")
    original_query = query.strip()
    expanded = [english for chinese, english in KEYWORD_ALIASES.items() if chinese in original_query]
    search_query = " ".join([original_query, *expanded])
    result = engine.search_stack(search_query, stack, max_results, diagnostics=True) if stack else \
        engine.search(search_query, domain, max_results, diagnostics=True)
    warnings = []
    if stack:
        scope = engine.STACK_CURRENT_APPLICABILITY.get(stack, "unverified")
        if not target_version:
            warnings.append(f"未声明 {stack} 目标版本；该参考集当前标注 {scope}，不得无版本审查直接应用。")
        elif (warning := _compatibility({}, stack, target_version, engine).get("warning")):
            warnings.append(warning)
        for row in result.get("results", []):
            row["compatibility"] = _compatibility(row, stack, target_version, engine)
            warning = row["compatibility"].get("warning")
            if warning and warning not in warnings:
                warnings.append(warning)
        if not result.get("results"):
            warnings.append("没有匹配的栈规则，不构成兼容性或最佳实践结论。")
    return {
        "schema_version": 1, "original_query": original_query, "keyword_expansion": expanded,
        "upstream": {"repository": provenance["repository"], "revision": provenance["revision"]},
        "policy": dict(POLICY), "vendor_payload": payload, "warnings": warnings, "result": result,
    }


def format_text(response):
    result = response["result"]
    lines = [f"UI/UX reference | {result.get('domain') or result.get('stack', '')} | {response['original_query']}",
             "只读参考：不保存、不执行示例、不安装依赖、不替换项目技术栈。"]
    lines.extend(f"WARNING: {warning}" for warning in response["warnings"])
    for index, row in enumerate(result.get("results", []), 1):
        lines.append(f"\n[{index}]")
        for key, value in row.items():
            if value not in (None, "", {}):
                lines.append(f"{key}: {json.dumps(value, ensure_ascii=False) if isinstance(value, dict) else value}")
    if not result.get("results"):
        lines.append("无匹配结果；请换更具体的关键词，不据此推断规则不存在。")
    return "\n".join(lines)


def main(argv=None):
    parser = argparse.ArgumentParser(description="只读、可复制的 UI/UX 参考检索；不会保存设计系统或安装库。")
    parser.add_argument("query", nargs="?")
    selector = parser.add_mutually_exclusive_group()
    selector.add_argument("--domain", "-d")
    selector.add_argument("--stack")
    parser.add_argument("--target-version", help="现有栈或平台版本；不是升级目标")
    parser.add_argument("--max-results", "-n", type=int, default=3)
    parser.add_argument("--json", action="store_true")
    parser.add_argument("--list", action="store_true", help="列出可检索领域及栈")
    parser.add_argument("--verify", action="store_true", help="校验固定上游的文件哈希")
    args = parser.parse_args(argv)
    try:
        if args.list or args.verify:
            engine, provenance, payload = load_engine()
            response = {"upstream": {"repository": provenance["repository"], "revision": provenance["revision"]},
                        "vendor_payload": payload, "domains": list(engine.CSV_CONFIG),
                        "stacks": list(engine.AVAILABLE_STACKS), "policy": dict(POLICY)}
            print(json.dumps(response, ensure_ascii=False, indent=2))
            return 0
        response = query_references(args.query, args.domain, args.stack, args.target_version, args.max_results)
    except (OSError, ValueError, KeyError) as error:
        parser.error(str(error))
    print(json.dumps(response, ensure_ascii=False, indent=2) if args.json else format_text(response))
    return 0


if __name__ == "__main__":
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8")
    raise SystemExit(main())
