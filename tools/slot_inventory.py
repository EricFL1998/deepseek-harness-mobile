#!/usr/bin/env python3
"""Scan the official slot registry and the native slot mapping, then report coverage.

Usage:
    python tools/slot_inventory.py [--root .] [--write docs/slot-inventory.md]

Exit code 1 when a mapped NativeSlot has no NativeSlotHost call site in App UI
code, so "mapped in table but never hosted" cannot pass CI.
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path


REGISTRY = Path("core/model/src/main/kotlin/com/deepseek/dsh/core/model/DshSlotRegistry.kt")
MAPPING = Path("plugin/native-sdk/src/main/kotlin/com/deepseek/dsh/plugin/nativesdk/slots/NativeSlotMapping.kt")
UI_ROOTS = [
    Path("app/src/main/kotlin"),
    Path("feature"),
    Path("plugin/native-host/src/main/kotlin"),
]
EXCLUDED_FILES = {
    Path("plugin/native-sdk/src/main/kotlin/com/deepseek/dsh/plugin/nativesdk/slots/NativeSlot.kt"),
    MAPPING,
}


def parse_registry(path: Path) -> dict[str, str]:
    text = path.read_text(encoding="utf-8")
    pairs = re.findall(r'const val (\w+): String = "([^"]+)"', text)
    return {name: slot for name, slot in pairs}


def parse_mapping(path: Path) -> tuple[dict[str, str], set[str]]:
    text = path.read_text(encoding="utf-8")
    mapped = dict(re.findall(r'"([A-Za-z0-9.\-]+)"\s+to\s+NativeSlot\.([A-Z_]+)', text))
    unsupported_block = re.search(
        r"val explicitlyUnsupported: Set<String> = setOf\((.*?)\n    \)",
        text,
        re.S,
    )
    unsupported = set(re.findall(r'"([a-z0-9.\-]+)"', unsupported_block.group(1))) if unsupported_block else set()
    return mapped, unsupported


def scan_hosts(root: Path) -> dict[str, list[str]]:
    hosts: dict[str, list[str]] = {}
    pattern = re.compile(r"NativeSlot\.([A-Z_]+)")
    for ui_root in UI_ROOTS:
        base = root / ui_root
        if not base.exists():
            continue
        files = [base] if base.is_file() else base.rglob("*.kt")
        for file in files:
            rel = file.relative_to(root)
            if rel in EXCLUDED_FILES or "samples" in rel.parts:
                continue
            text = file.read_text(encoding="utf-8", errors="ignore")
            for match in pattern.finditer(text):
                hosts.setdefault(match.group(1), []).append(str(rel))
    return hosts


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", default=".")
    parser.add_argument("--write", default="")
    args = parser.parse_args()
    root = Path(args.root).resolve()

    registry = parse_registry(root / REGISTRY)
    mapped, unsupported = parse_mapping(root / MAPPING)
    hosts = scan_hosts(root)

    all_slots = set(registry.values())
    unknown_mapped = set(mapped) - all_slots
    unknown_unsupported = unsupported - all_slots
    uncovered = all_slots - set(mapped) - unsupported
    unhosted = sorted(set(mapped.values()) - set(hosts))

    lines: list[str] = []
    lines.append("# 官方 slot 覆盖清单（自动生成）")
    lines.append("")
    lines.append("> 由 `tools/slot_inventory.py` 生成。先跑扫描，再写 UI；")
    lines.append("> `mapped` 是 `NativeSlotMapping` 的声明，`hosted` 是 App 源码里真实出现的")
    lines.append("> `NativeSlotHost`/`NativeSlot.X` 引用。两者不一致时工具失败。")
    lines.append("")
    lines.append(f"- 官方 slot 常量：{len(registry)}")
    lines.append(f"- 已映射：{len(mapped)}")
    lines.append(f"- 明确不支持：{len(unsupported)}")
    lines.append(f"- 未覆盖：{len(uncovered)}")
    lines.append(f"- 声明了映射但未找到宿主：{len(unhosted)}")
    lines.append("")
    lines.append("| 官方 slot | NativeSlot | 宿主文件 |")
    lines.append("| --- | --- | --- |")
    for name in sorted(registry):
        slot = registry[name]
        if slot in mapped:
            files = sorted(set(hosts.get(mapped[slot], [])))
            host_text = "<br>".join(files[:3]) if files else "**未找到**"
            lines.append(f"| `{slot}` | `{mapped[slot]}` | {host_text} |")
        elif slot in unsupported:
            lines.append(f"| `{slot}` | 未接入（显式） | — |")
        else:
            lines.append(f"| `{slot}` | **未映射** | — |")
    lines.append("")
    if uncovered:
        lines.append("## 失败：未覆盖的官方 slot")
        lines.extend(f"- `{slot}`" for slot in sorted(uncovered))
        lines.append("")
    if unknown_mapped:
        lines.append("## 失败：映射表引用了不存在的官方 slot")
        lines.extend(f"- `{slot}`" for slot in sorted(unknown_mapped))
        lines.append("")
    if unknown_unsupported:
        lines.append("## 失败：不支持集合引用了不存在的官方 slot")
        lines.extend(f"- `{slot}`" for slot in sorted(unknown_unsupported))
        lines.append("")
    if unhosted:
        lines.append("## 失败：已映射但没有宿主容器")
        lines.extend(f"- `{slot}`" for slot in unhosted)
        lines.append("")

    report = "\n".join(lines).rstrip() + "\n"
    if args.write:
        (root / args.write).write_text(report, encoding="utf-8")
    print(report)

    if uncovered or unknown_mapped or unknown_unsupported or unhosted:
        print("slot inventory check FAILED", file=sys.stderr)
        return 1
    print("slot inventory check OK", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
