#!/usr/bin/env python3
"""Recognize official UI mount points from the DSH upstream client source.

The goal is to stop discovering slots from screenshots. This scanner reads the
upstream client packages and reports, for every registration:

  official slot -> entry id -> component -> source file -> data dependencies

It then joins that with the native side (NativeSlotMapping + NativeSlot.X host
references) and writes a machine-readable JSON and a Markdown report.

Usage:
    python tools/official_ui_inventory.py
    python tools/official_ui_inventory.py --check
    python tools/official_ui_inventory.py --upstream dsh-upstream \
        --write docs/official-ui-inventory.md \
        --json docs/official-ui-inventory.json

Exit code 1 with --check when an official slot is unmapped or a mapped slot has
no host container. Registration entry coverage gaps are reported as warnings.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass, field, asdict
from pathlib import Path

REGISTRY = Path("core/model/src/main/kotlin/com/deepseek/dsh/core/model/DshSlotRegistry.kt")
MAPPING = Path(
    "plugin/native-sdk/src/main/kotlin/com/deepseek/dsh/plugin/nativesdk/slots/NativeSlotMapping.kt"
)
UI_ROOTS = [
    Path("app/src/main/kotlin"),
    Path("feature"),
    Path("plugin/native-host/src/main/kotlin"),
]
EXCLUDED_FILES = {
    Path("plugin/native-sdk/src/main/kotlin/com/deepseek/dsh/plugin/nativesdk/slots/NativeSlot.kt"),
    MAPPING,
}
UPSTREAM_CLIENTS = "packages/client"
REGISTER_RE = re.compile(
    r"slots\.register\(\s*\{(?P<body>.*?)\}\s*,\s*(?P<component>[A-Za-z_][\w.]*)\s*\)",
    re.S,
)
NAME_RE = re.compile(r"\bname:\s*['\"]([^'\"]+)['\"]")
ID_RE = re.compile(r"\bid:\s*['\"]([^'\"]+)['\"]")
ORDER_RE = re.compile(r"\border:\s*(-?\d+)")
INJECT_RE = re.compile(r"\binject:\s*(?:\(\s*\)\s*=>\s*)?\(\s*\{([^}]*)\}\s*\)")
USE_PROJECTION_RE = re.compile(r"useProjection\(\s*['\"]([^'\"]+)['\"]")
USE_HOOK_RE = re.compile(r"\b(useChat|useSession|useProjection|usePerformanceUsage)\b")
CHILDREN_RE = re.compile(r"children:\s*\{(.*?)\n\s*\}", re.S)
CHILD_KEY_RE = re.compile(r"['\"]([a-zA-Z0-9.\-]+)['\"]\s*:\s*\{")
SLOT_CONST_RE = re.compile(r"const val (\w+): String = \"([^\"]+)\"")
MAPPING_RE = re.compile(r"\"([A-Za-z0-9.\-]+)\"\s+to\s+NativeSlot\.([A-Z_]+)")


@dataclass
class Registration:
    slot: str
    entry: str
    order: int | None
    component: str
    package: str
    file: str
    hooks: list[str] = field(default_factory=list)
    projections: list[str] = field(default_factory=list)
    injected: list[str] = field(default_factory=list)


def parse_registry(root: Path) -> dict[str, str]:
    text = (root / REGISTRY).read_text(encoding="utf-8")
    return {name: slot for name, slot in SLOT_CONST_RE.findall(text)}


def parse_mapping(root: Path) -> dict[str, str]:
    text = (root / MAPPING).read_text(encoding="utf-8")
    return dict(MAPPING_RE.findall(text))


def parse_dock_entries(root: Path) -> set[str]:
    """Native sub-destinations explicitly split out of one official slot."""
    text = (root / MAPPING).read_text(encoding="utf-8")
    return set(re.findall(r"NativeSlot\.(COMPOSER_DOCK_[A-Z_]+)", text))


def package_of(path: Path) -> str:
    parts = path.parts
    if "packages" in parts:
        idx = parts.index("packages")
        if idx + 2 < len(parts):
            return "/".join(parts[idx : idx + 2])
    return path.parent.as_posix()


def scan_upstream(upstream: Path, root: Path, official_slots: set[str]) -> tuple[list[Registration], list[str]]:
    base = upstream / UPSTREAM_CLIENTS
    registrations: list[Registration] = []
    child_declarations: list[str] = []
    if not base.exists():
        return registrations, child_declarations
    for file in sorted(base.rglob("*")):
        if file.suffix not in {".ts", ".tsx"}:
            continue
        rel_parts = file.relative_to(root).parts
        if "tests" in rel_parts or file.name.endswith((".spec.ts", ".spec.tsx")):
            continue
        try:
            text = file.read_text(encoding="utf-8", errors="ignore")
        except OSError:
            continue
        for match in REGISTER_RE.finditer(text):
            body = match.group("body")
            name = NAME_RE.search(body)
            if name is None or name.group(1) not in official_slots:
                continue
            entry_match = ID_RE.search(body)
            order_match = ORDER_RE.search(body)
            component = match.group("component")
            component_file = locate_component(file, component)
            hooks: list[str] = []
            projections: list[str] = []
            injected: list[str] = []
            if component_file is not None:
                component_text = component_file.read_text(encoding="utf-8", errors="ignore")
                hooks = sorted(set(USE_HOOK_RE.findall(component_text)))
                projections = sorted(set(USE_PROJECTION_RE.findall(component_text)))
            inject_match = INJECT_RE.search(body)
            if inject_match:
                injected = sorted(
                    part.strip()
                    for part in inject_match.group(1).split(",")
                    if part.strip()
                )
            registrations.append(
                Registration(
                    slot=name.group(1),
                    entry=entry_match.group(1) if entry_match else "",
                    order=int(order_match.group(1)) if order_match else None,
                    component=component,
                    package=package_of(file.relative_to(root)),
                    file=str(file.relative_to(root)),
                    hooks=hooks,
                    projections=projections,
                    injected=injected,
                )
            )
        for children in CHILDREN_RE.finditer(text):
            for child in CHILD_KEY_RE.finditer(children.group(1)):
                if child.group(1) in official_slots:
                    child_declarations.append(
                        f"{child.group(1)} <- {file.relative_to(root)}"
                    )
    return registrations, child_declarations


def locate_component(owner: Path, component: str) -> Path | None:
    """Resolve a registered component name to its source file, same package first."""
    simple = component.split(".")[-1]
    candidates = [owner.parent / f"{simple}.tsx", owner.parent / f"{simple}.ts"]
    package_root = owner
    while package_root.parent != package_root and package_root.name != "client":
        package_root = package_root.parent
    for candidate in candidates:
        if candidate.is_file():
            return candidate
    if package_root.is_dir():
        for candidate in package_root.rglob(f"{simple}.tsx"):
            return candidate
        for candidate in package_root.rglob(f"{simple}.ts"):
            return candidate
    return None


def scan_hosts(root: Path) -> dict[str, list[str]]:
    hosts: dict[str, list[str]] = {}
    pattern = re.compile(r"NativeSlot\.([A-Z_]+)")
    for ui_root in UI_ROOTS:
        base = root / ui_root
        if not base.exists():
            continue
        for file in [base] if base.is_file() else base.rglob("*.kt"):
            rel = file.relative_to(root)
            if rel in EXCLUDED_FILES or "samples" in rel.parts:
                continue
            text = file.read_text(encoding="utf-8", errors="ignore")
            for match in pattern.finditer(text):
                hosts.setdefault(match.group(1), []).append(str(rel))
    return hosts


def _match_brace(text: str, start: int) -> int:
    """Return the index of the `}` matching the `{` at start, string/comment aware."""
    depth = 0
    i = start
    quote: str | None = None
    while i < len(text):
        ch = text[i]
        if quote is not None:
            if ch == "\\":
                i += 2
                continue
            if ch == quote:
                quote = None
            i += 1
            continue
        if ch in {"'", '"', "`"}:
            quote = ch
            i += 1
            continue
        if ch == "/" and i + 1 < len(text) and text[i + 1] == "/":
            newline = text.find("\n", i)
            i = len(text) if newline < 0 else newline + 1
            continue
        if ch == "/" and i + 1 < len(text) and text[i + 1] == "*":
            end = text.find("*/", i + 2)
            i = len(text) if end < 0 else end + 2
            continue
        if ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
            if depth == 0:
                return i
        i += 1
    return -1


def iter_register_calls(text: str):
    """Yield (registration body, component identifier) for balanced `slots.register({...}, C)` calls.

    Works for both upstream TS sources and the compiled `lib/client.js` shipped in an
    installed DSH app, so third-party developers do not need the upstream checkout.
    """
    needle = "slots.register("
    pos = 0
    while True:
        index = text.find(needle, pos)
        if index < 0:
            return
        cursor = index + len(needle)
        while cursor < len(text) and text[cursor].isspace():
            cursor += 1
        if cursor >= len(text) or text[cursor] != "{":
            pos = cursor
            continue
        end = _match_brace(text, cursor)
        if end < 0:
            pos = cursor + 1
            continue
        body = text[cursor : end + 1]
        after = end + 1
        while after < len(text) and text[after].isspace():
            after += 1
        if after < len(text) and text[after] == ",":
            after += 1
            while after < len(text) and text[after].isspace():
                after += 1
        component_match = re.match(r"[A-Za-z_$][\w$]*", text[after:])
        yield body, component_match.group(0) if component_match else ""
        pos = end + 1


def scan_installed_clients(
    client_dirs: list[Path], official_slots: set[str]
) -> tuple[list[Registration], list[str]]:
    """Scan installed `@deepseek-ai/dsh-client-*` packages (compiled lib/*.js).

    This is the mode for SDK users / third-party plugin developers: the App ships the
    official client packages, but they do not have the upstream repository.
    """
    registrations: list[Registration] = []
    child_declarations: list[str] = []
    packages: list[Path] = []
    for base in client_dirs:
        if not base.exists():
            continue
        if base.name.startswith("dsh-client-"):
            packages.append(base)
        else:
            packages.extend(sorted(path for path in base.glob("dsh-client-*") if path.is_dir()))
    for package in packages:
        lib = package / "lib"
        if not lib.exists():
            continue
        for file in sorted(lib.glob("*.js")):
            try:
                text = file.read_text(encoding="utf-8", errors="ignore")
            except OSError:
                continue
            for body, component in iter_register_calls(text):
                name = NAME_RE.search(body)
                if name is None or name.group(1) not in official_slots:
                    continue
                entry = ID_RE.search(body)
                order = ORDER_RE.search(body)
                inject = INJECT_RE.search(body)
                injected = (
                    sorted(part.strip() for part in inject.group(1).split(",") if part.strip())
                    if inject
                    else []
                )
                registrations.append(
                    Registration(
                        slot=name.group(1),
                        entry=entry.group(1) if entry else "",
                        order=int(order.group(1)) if order else None,
                        component=component,
                        package=package.name,
                        file=str(file.relative_to(package.parent)),
                        hooks=[],
                        projections=[],
                        injected=injected,
                    )
                )
            for children in CHILDREN_RE.finditer(text):
                for child in CHILD_KEY_RE.finditer(children.group(1)):
                    if child.group(1) in official_slots:
                        child_declarations.append(
                            f"{child.group(1)} <- {package.name}/{file.name}"
                        )
    return registrations, child_declarations


def build_report(
    root: Path,
    upstream: Path | None,
    client_dirs: list[Path] | None = None,
) -> tuple[str, dict, list[str], list[str]]:
    registry = parse_registry(root)
    official_slots = set(registry.values())
    mapped = parse_mapping(root)
    hosts = scan_hosts(root)
    dock_entries = parse_dock_entries(root)
    if client_dirs:
        registrations, child_declarations = scan_installed_clients(client_dirs, official_slots)
    elif upstream is not None:
        registrations, child_declarations = scan_upstream(upstream, root, official_slots)
    else:
        registrations, child_declarations = [], []

    by_slot: dict[str, list[Registration]] = {}
    for registration in registrations:
        by_slot.setdefault(registration.slot, []).append(registration)
    for entries in by_slot.values():
        entries.sort(key=lambda item: (item.order is None, item.order or 0, item.entry))

    uncovered = sorted(official_slots - set(mapped))
    unhosted = sorted(set(mapped.values()) - set(hosts))
    unsplit_dock = [
        registration
        for registration in by_slot.get("conversation.composer.dock", [])
        if registration.entry and "COMPOSER_DOCK_" + re.sub(r"[^A-Za-z0-9]", "_", registration.entry).upper()
        not in dock_entries
    ]

    lines: list[str] = []
    if client_dirs:
        source_desc = "已安装官方客户端包（dsh-client-*/lib/*.js，无需 upstream）"
    else:
        source_desc = "dsh-upstream/packages/client"
    lines.append("# 官方 UI / 落点清单（自动生成）")
    lines.append("")
    lines.append(f"> 由 `tools/official_ui_inventory.py` 扫描 {source_desc} 生成。")
    lines.append("> 不再靠截图找 UI：每个官方 slot 的注册条目、组件、数据依赖与原生覆盖状态都在这里。")
    lines.append("")
    lines.append("## 概览")
    lines.append("")
    lines.append(f"- 官方 slot：{len(official_slots)}")
    lines.append(f"- 官方注册条目：{len(registrations)}")
    lines.append(f"- 已映射 NativeSlot：{len(mapped)}")
    lines.append(f"- 未映射官方 slot：{len(uncovered)}")
    lines.append(f"- 已映射但无宿主：{len(unhosted)}")
    lines.append(f"- composer.dock 注册条目未拆成原生落点：{len(unsplit_dock)}")
    lines.append("")
    lines.append("## 落点覆盖")
    lines.append("")
    lines.append("| 官方 slot | 原生落点 | 宿主 | 注册条目 |")
    lines.append("| --- | --- | --- | --- |")
    for slot in sorted(official_slots):
        entries = by_slot.get(slot, [])
        entry_text = ", ".join(
            f"`{item.entry or item.component}`" for item in entries
        ) or "—"
        if slot in mapped:
            native = f"`{mapped[slot]}`"
            files = sorted(set(hosts.get(mapped[slot], [])))
            host_text = "<br>".join(files[:2]) if files else "**未找到**"
        else:
            native = "**未映射**"
            host_text = "—"
        lines.append(f"| `{slot}` | {native} | {host_text} | {entry_text} |")
    lines.append("")
    lines.append("## 注册条目明细")
    lines.append("")
    lines.append("| 官方 slot | entry | order | 组件 | 包 | 数据依赖 |")
    lines.append("| --- | --- | --- | --- | --- | --- |")
    for registration in sorted(
        registrations, key=lambda item: (item.slot, item.order is None, item.order or 0, item.entry)
    ):
        deps: list[str] = []
        deps.extend(f"`{hook}`" for hook in registration.hooks)
        deps.extend(f"projection:{item}" for item in registration.projections)
        deps.extend(f"inject:{item}" for item in registration.injected)
        lines.append(
            f"| `{registration.slot}` | `{registration.entry or '—'}` | "
            f"{registration.order if registration.order is not None else '—'} | "
            f"`{registration.component}` | `{registration.package}` | "
            f"{', '.join(deps) if deps else '—'} |"
        )
    lines.append("")
    if child_declarations:
        lines.append("## 官方 children 关系")
        lines.append("")
        for declaration in sorted(set(child_declarations)):
            lines.append(f"- `{declaration}`")
        lines.append("")
    if uncovered:
        lines.append("## 缺口：官方 slot 没有原生落点")
        lines.extend(f"- `{slot}`" for slot in uncovered)
        lines.append("")
    if unhosted:
        lines.append("## 缺口：映射了但没有原生宿主")
        lines.extend(f"- `{slot}`" for slot in unhosted)
        lines.append("")
    if unsplit_dock:
        lines.append("## 提示：dock 条目尚未拆成独立原生落点")
        for registration in unsplit_dock:
            lines.append(
                f"- `{registration.entry}`（{registration.component}，{registration.file}）"
            )
        lines.append("")

    report = "\n".join(lines).rstrip() + "\n"
    payload = {
        "source": source_desc,
        "officialSlots": sorted(official_slots),
        "registrations": [asdict(item) for item in registrations],
        "mapping": mapped,
        "hosts": {key: sorted(set(value)) for key, value in hosts.items()},
        "uncoveredSlots": uncovered,
        "unhostedDestinations": unhosted,
        "unsplitDockEntries": [
            {"slot": item.slot, "entry": item.entry, "component": item.component, "file": item.file}
            for item in unsplit_dock
        ],
        "children": sorted(set(child_declarations)),
    }
    return report, payload, uncovered, unhosted


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", default=".")
    parser.add_argument("--upstream", default="")
    parser.add_argument(
        "--rootfs",
        default="",
        help="Installed DSH rootfs; scanner reads node_modules/@deepseek-ai/dsh-client-* from it.",
    )
    parser.add_argument(
        "--client-dir",
        action="append",
        default=[],
        help="Directory containing dsh-client-* packages (repeatable).",
    )
    parser.add_argument("--write", default="")
    parser.add_argument("--json", default="")
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    root = Path(args.root).resolve()

    client_dirs: list[Path] = []
    if args.rootfs:
        rootfs = Path(args.rootfs).resolve()
        client_dirs.append(
            rootfs
            / "usr/local/lib/node_modules/@deepseek-ai/dsh/node_modules/@deepseek-ai"
        )
    for value in args.client_dir:
        client_dirs.append(Path(value).resolve())

    upstream: Path | None = None
    if args.upstream:
        candidate = Path(args.upstream)
        if not candidate.is_absolute():
            candidate = root / candidate
        if (candidate / UPSTREAM_CLIENTS).exists():
            upstream = candidate
        else:
            print(
                f"official UI inventory: upstream source not found at {candidate}/{UPSTREAM_CLIENTS}",
                file=sys.stderr,
            )
    if upstream is None and not client_dirs:
        candidate = root / "dsh-upstream"
        if (candidate / UPSTREAM_CLIENTS).exists():
            upstream = candidate
    if upstream is None and not client_dirs:
        print(
            "official UI inventory: no source. Pass --rootfs <installed DSH rootfs> or "
            "--client-dir <node_modules/@deepseek-ai> (third-party environments have no "
            "dsh-upstream checkout).",
            file=sys.stderr,
        )
        return 2
    if client_dirs and not any(path.exists() for path in client_dirs):
        print(
            "official UI inventory: no dsh-client-* packages found under " + ", ".join(str(p) for p in client_dirs),
            file=sys.stderr,
        )
        return 2

    report, payload, uncovered, unhosted = build_report(root, upstream, client_dirs)
    if args.write:
        (root / args.write).write_text(report, encoding="utf-8")
    if args.json:
        (root / args.json).write_text(
            json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
    print(report)
    if args.check and (uncovered or unhosted):
        print("official UI inventory check FAILED", file=sys.stderr)
        return 1
    print("official UI inventory check OK", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
