#!/usr/bin/env python3
"""Scan a third-party plugin's own source and recognize its native-UI surface.

Input is the plugin package a developer already has (no DSH upstream checkout
needed): either its `src/` TypeScript, its `lib/client.js.map` (which embeds the
original TSX in `sourcesContent`), or the compiled `lib/client.js`.

Output:
  - slots the plugin registers (slot name, entry id, order, component, mode);
  - the native destination/host status for each slot;
  - RPC endpoints and projection/injected data the UI needs;
  - component JSX tree + visible text/buttons;
  - a JSON manifest that can drive native conversion or a dsh-ui spec.

Usage:
  python tools/plugin_ui_scanner.py --plugin path/to/dsh-plugin-x
  python tools/plugin_ui_scanner.py --plugin path/to/package --check
  python tools/plugin_ui_scanner.py --plugin path --write docs/plugin-x-ui.md --json docs/plugin-x-ui.json
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass, field, asdict
from pathlib import Path

MAPPING = Path(
    "plugin/native-sdk/src/main/kotlin/com/deepseek/dsh/plugin/nativesdk/slots/NativeSlotMapping.kt"
)
REGISTRY = Path("core/model/src/main/kotlin/com/deepseek/dsh/core/model/DshSlotRegistry.kt")
UI_ROOTS = [
    Path("app/src/main/kotlin"),
    Path("feature"),
    Path("plugin/native-host/src/main/kotlin"),
]
NAME_RE = re.compile(r"\bname:\s*['\"]([^'\"]+)['\"]")
ID_RE = re.compile(r"\bid:\s*['\"]([^'\"]+)['\"]")
ORDER_RE = re.compile(r"\border:\s*(-?\d+)")
MODE_RE = re.compile(r"\bmode:\s*['\"]?([A-Za-z]+)['\"]?")
PROJECTION_RE = re.compile(r"useProjection\(\s*['\"]([^'\"]+)['\"]")
HOOK_RE = re.compile(r"\b(useChat|useSession|useProjection|usePerformanceUsage)\b")
RPC_SUFFIX_RE = re.compile(r"RPC_PREFIX\}\s*([A-Za-z0-9_\-]+)")
RPC_CALL_RE = re.compile(r"\bcall\(\s*['\"]([A-Za-z0-9_\-]+)['\"]")
T_CALL_RE = re.compile(r"\bt\(\s*['\"]([^'\"]+)['\"]")
POSITION_RE = re.compile(
    r"useAnchoredPosition|createPortal|anchorRef|getBoundingClientRect|"
    r"role\s*=\s*['\"]dialog['\"]|Popover|popover|position\s*:\s*['\"](?:fixed|absolute)['\"]|"
    r"side\s*:\s*['\"]top['\"]|align\s*:\s*['\"]end['\"]"
)
TEXT_RE = re.compile(r">([^<>{}]{2,80})<")
JSX_TAG_RE = re.compile(r"<\s*(/?)\s*([A-Za-z][\w.]*)([^>]*)>", re.S)
JSX_ATTR_RE = re.compile(r"([A-Za-z_][\w-]*)\s*=\s*\"([^\"]*)\"|([A-Za-z_][\w-]*)\s*=\s*'([^']*)'")
KOTLIN_CONST_RE = re.compile(r"const val (\w+): String = \"([^\"]+)\"")
MAPPING_RE = re.compile(r"\"([A-Za-z0-9.\-]+)\"\s+to\s+NativeSlot\.([A-Z_]+)")


@dataclass
class PluginSlot:
    slot: str
    entry: str
    order: int | None
    mode: str
    component: str
    source: str
    native: str
    hosted: bool
    implemented: bool = False
    data: list[str] = field(default_factory=list)
    texts: list[str] = field(default_factory=list)
    buttons: list[str] = field(default_factory=list)
    placement: list[str] = field(default_factory=list)
    tree: list[dict] = field(default_factory=list)


@dataclass
class PluginUI:
    package_name: str
    version: str
    sources: list[str]
    slots: list[PluginSlot]
    rpc: list[str]
    locales: dict[str, dict[str, str]]
    warnings: list[str]


def match_brace(text: str, start: int) -> int:
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
        end = match_brace(text, cursor)
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


def load_sources(plugin: Path) -> tuple[str, str, dict[str, str], list[str]]:
    """Return (package name, version, virtual-path -> source text, source list)."""
    package_name = plugin.name
    version = ""
    package_json = plugin / "package.json"
    if package_json.is_file():
        try:
            data = json.loads(package_json.read_text(encoding="utf-8"))
            package_name = data.get("name", package_name)
            version = data.get("version", "")
        except Exception:
            pass
    sources: dict[str, str] = {}
    map_files = sorted(plugin.rglob("*.js.map"))
    for map_file in map_files:
        try:
            data = json.loads(map_file.read_text(encoding="utf-8"))
        except Exception:
            continue
        names = data.get("sources") or []
        contents = data.get("sourcesContent") or []
        for name, content in zip(names, contents):
            if not content:
                continue
            if not name.endswith((".ts", ".tsx", ".js", ".jsx")):
                continue
            virtual = str(map_file.parent / name).replace("\\", "/")
            sources[virtual] = content
    if not sources:
        for pattern in ("src/**/*.ts", "src/**/*.tsx"):
            for file in plugin.glob(pattern):
                sources[str(file)] = file.read_text(encoding="utf-8", errors="ignore")
    if not sources:
        for file in plugin.glob("lib/*.js"):
            sources[str(file)] = file.read_text(encoding="utf-8", errors="ignore")
    return package_name, version, sources, sorted(sources)


def component_source(sources: dict[str, str], component: str) -> tuple[str, str]:
    simple = component.split(".")[-1]
    for path, text in sources.items():
        if Path(path).stem == simple:
            return path, text
    # Inline component registered as an arrow function: fall back to the registering file.
    for path, text in sources.items():
        if f"function {simple}" in text or f"const {simple}" in text:
            return path, text
    return "", ""


def jsx_tree(source: str) -> tuple[list[dict], list[str], list[str]]:
    """Heuristic JSX tree: tag hierarchy, visible text, button labels."""
    root: list[dict] = []
    stack: list[dict] = []
    texts: list[str] = []
    buttons: list[str] = []
    cursor = 0
    for match in JSX_TAG_RE.finditer(source):
        between = source[cursor:match.start()]
        if between:
            clean = " ".join(between.split())
            if clean and not clean.startswith(("//", "/*", "*")):
                texts.extend(
                    item.strip()
                    for item in re.findall(r">([^<>{}]{2,80})<", between)
                    if item.strip()
                )
        cursor = match.end()
        closing, tag, attrs = match.group(1), match.group(2), match.group(3)
        if closing:
            if stack:
                stack.pop()
            continue
        node = {"type": tag, "text": "", "children": []}
        if attrs.rstrip().endswith("/"):
            attrs = attrs.rstrip()[:-1]
        labels = [a or c for a, _, c, _ in JSX_ATTR_RE.findall(attrs)]
        text_literals = re.findall(r"['\"]([^'\"]{2,80})['\"]", attrs)
        for literal in text_literals:
            if literal.startswith(("http", "dsh-", "flex", "absolute")):
                continue
            node["text"] = literal
            break
        if node["text"]:
            texts.append(node["text"])
        if tag == "Button":
            buttons.append(node["text"] or "?".join(labels))
        if stack:
            stack[-1]["children"].append(node)
        else:
            root.append(node)
        stack.append(node)
    return root[:3], dedupe(texts)[:30], dedupe(buttons)[:20]


def dedupe(items: list[str]) -> list[str]:
    seen: set[str] = set()
    result: list[str] = []
    for item in items:
        if item and item not in seen:
            seen.add(item)
            result.append(item)
    return result


def parse_mapping(root: Path) -> dict[str, str]:
    text = (root / MAPPING).read_text(encoding="utf-8")
    return dict(MAPPING_RE.findall(text))


def parse_registry(root: Path) -> set[str]:
    text = (root / REGISTRY).read_text(encoding="utf-8")
    return set(slot for _, slot in KOTLIN_CONST_RE.findall(text))


def scan_hosts(root: Path) -> set[str]:
    hosts: set[str] = set()
    pattern = re.compile(r"NativeSlot\.([A-Z_]+)")
    for ui_root in UI_ROOTS:
        base = root / ui_root
        if not base.exists():
            continue
        for file in base.rglob("*.kt"):
            rel = file.relative_to(root)
            if "samples" in rel.parts:
                continue
            hosts.update(pattern.findall(file.read_text(encoding="utf-8", errors="ignore")))
    return hosts


def locale_tables(plugin: Path) -> dict[str, dict[str, str]]:
    result: dict[str, dict[str, str]] = {}
    for file in plugin.rglob("*.json"):
        if "locale" not in file.parts:
            continue
        try:
            data = json.loads(file.read_text(encoding="utf-8"))
        except Exception:
            continue
        if isinstance(data, dict):
            result[file.stem] = {str(key): str(value) for key, value in data.items() if isinstance(value, str)}
    return result


def scan_companion(path: Path) -> tuple[set[str], bool, bool, bool]:
    """Find NativeSlot contributions, PageContent pages, and popup/anchoring primitives."""
    slots: set[str] = set()
    pages = False
    has_popup = False
    has_dialog = False
    files = [path] if path.is_file() else list(path.rglob("*.kt"))
    for file in files:
        text = file.read_text(encoding="utf-8", errors="ignore")
        slots.update(re.findall(r"NativeSlot\.([A-Z_]+)", text))
        if "PageRegistry.register" in text or ": PageContent" in text:
            pages = True
        has_popup = has_popup or "Popup(" in text or "PopupPositionProvider" in text or "PluginPopover(" in text
        has_dialog = has_dialog or "Dialog(" in text
    return slots, pages, has_popup, has_dialog


def scan(plugin: Path, root: Path, companion: Path | None = None) -> PluginUI:
    package_name, version, sources, source_list = load_sources(plugin)
    mapping = parse_mapping(root)
    official = parse_registry(root)
    hosts = scan_hosts(root)
    if companion:
        companion_slots, companion_pages, companion_popup, companion_dialog = scan_companion(companion)
    else:
        companion_slots, companion_pages, companion_popup, companion_dialog = set(), False, False, False
    warnings: list[str] = []
    slot_items: list[PluginSlot] = []
    rpc_methods: set[str] = set()
    for path, text in sources.items():
        rpc_methods.update(RPC_CALL_RE.findall(text))
        rpc_methods.update(RPC_SUFFIX_RE.findall(text))
        for body, component in iter_register_calls(text):
            name = NAME_RE.search(body)
            if name is None:
                continue
            slot = name.group(1)
            if slot not in official:
                warnings.append(f"注册了未知/非官方 slot: {slot} ({path})")
            entry = ID_RE.search(body)
            order = ORDER_RE.search(body)
            mode = MODE_RE.search(body)
            component_path, component_text = component_source(sources, component)
            tree, texts, buttons = jsx_tree(component_text or text)
            placement = sorted(set(POSITION_RE.findall(component_text or text)))
            native = mapping.get(slot, "")
            if slot == "conversation.composer.dock" and entry:
                candidate = "COMPOSER_DOCK_" + re.sub(r"[^A-Za-z0-9]", "_", entry.group(1)).upper()
                if candidate in hosts:
                    native = candidate
            data = sorted(
                set(PROJECTION_RE.findall(component_text or ""))
                | {f"rpc:{item}" for item in RPC_CALL_RE.findall(component_text or "")}
            )
            hosted = bool(native and native in hosts)
            implemented = bool(
                (native and native in companion_slots)
                or (companion_pages and slot == "settings.section")
            )
            if not native:
                warnings.append(f"slot 没有 NativeSlot 映射: {slot} ({component})")
            elif not hosted:
                warnings.append(f"NativeSlot 已映射但没有宿主: {native} ({slot})")
            if companion is not None and not implemented:
                warnings.append(f"插件有 UI，原生伴侣未实现: {slot} / {component}")
            anchored = any(
                hint in placement
                for hint in ("useAnchoredPosition", "createPortal", "Popover", "popover")
            )
            if companion is not None and anchored and not companion_popup:
                warnings.append(
                    f"插件使用锚定浮层，但伴侣没有 Popup/PopupPositionProvider: {slot} / {component}"
                )
            slot_items.append(
                PluginSlot(
                    slot=slot,
                    entry=entry.group(1) if entry else "",
                    order=int(order.group(1)) if order else None,
                    mode=(mode.group(1).upper() if mode else "ENHANCE"),
                    component=component,
                    source=component_path or path,
                    native=native,
                    hosted=hosted,
                    implemented=implemented,
                    data=data,
                    texts=texts,
                    buttons=buttons,
                    placement=placement,
                    tree=tree,
                )
            )
    # Deduplicate identical registrations from map + src duplicates.
    unique: dict[tuple, PluginSlot] = {}
    for item in slot_items:
        key = (item.slot, item.entry, item.component, item.source)
        unique[key] = item
    return PluginUI(
        package_name=package_name,
        version=version,
        sources=source_list,
        slots=sorted(unique.values(), key=lambda item: (item.slot, item.order or 0)),
        rpc=sorted(rpc_methods),
        locales=locale_tables(plugin),
        warnings=dedupe(warnings),
    )


def report(ui: PluginUI) -> str:
    lines = [
        f"# 插件 UI / 功能清单：{ui.package_name}",
        "",
        "> 由 `tools/plugin_ui_scanner.py` 直接扫描插件源码（含 `.js.map` 内嵌 TSX）生成。",
        "> 不再需要用户截图：slot、entry、组件、RPC、文案与组件树都在这里。",
        "",
        f"- 包：`{ui.package_name}` {ui.version}",
        f"- 源文件：{len(ui.sources)}",
        f"- 注册落点：{len(ui.slots)}",
        f"- RPC 端点：{len(ui.rpc)}",
        "",
        "## 落点与原生状态",
        "",
        "| slot | entry | order | component | 原生落点 | 宿主 | 原生实现 | 数据依赖 |",
        "| --- | --- | --- | --- | --- | --- | --- | --- |",
    ]
    for item in ui.slots:
        data = ", ".join(f"`{entry}`" for entry in item.data) or "—"
        lines.append(
            f"| `{item.slot}` | `{item.entry or '—'}` | {item.order if item.order is not None else '—'} | "
            f"`{item.component}` | `{item.native or '未映射'}` | "
            f"{'是' if item.hosted else '**否**'} | "
            f"{'是' if item.implemented else '**否**'} | {data} |"
        )
    lines += ["", "## 组件与可见 UI", ""]
    for item in ui.slots:
        if not (item.texts or item.buttons or item.tree):
            continue
        lines.append(f"### `{item.component}`（{item.slot}）")
        lines.append("")
        if item.texts:
            lines.append("- 文案：" + "、".join(f"`{text}`" for text in item.texts))
        if item.buttons:
            lines.append("- 动作：" + "、".join(f"`{button}`" for button in item.buttons))
        if item.placement:
            lines.append("- 定位/交互：" + "、".join(f"`{hint}`" for hint in item.placement))
        if item.tree:
            lines.append("")
            lines.append("```json")
            lines.append(json.dumps(item.tree, ensure_ascii=False, indent=2)[:6000])
            lines.append("```")
        lines.append("")
    if ui.rpc:
        lines += ["## RPC / 数据", "", "| 端点/方法 |", "| --- |"]
        lines.extend(f"| `{method}` |" for method in ui.rpc)
        lines.append("")
    if ui.locales:
        lines += ["## 本地化文案", ""]
        for locale, table in ui.locales.items():
            lines.append(f"### {locale}")
            lines.append("")
            for key, value in list(table.items())[:80]:
                lines.append(f"- `{key}`: {value}")
            lines.append("")
    if ui.warnings:
        lines += ["## 需要处理", ""]
        lines.extend(f"- {warning}" for warning in ui.warnings)
        lines.append("")
    return "\n".join(lines).rstrip() + "\n"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--plugin", required=True, help="Plugin package directory")
    parser.add_argument("--companion", default="", help="Native companion source/dir to check implemented slots")
    parser.add_argument("--root", default=".")
    parser.add_argument("--write", default="")
    parser.add_argument("--json", default="")
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    root = Path(args.root).resolve()
    plugin = Path(args.plugin).resolve()
    if not plugin.exists():
        print(f"plugin not found: {plugin}", file=sys.stderr)
        return 2
    ui = scan(plugin, root, Path(args.companion).resolve() if args.companion else None)
    text = report(ui)
    if args.write:
        (root / args.write).write_text(text, encoding="utf-8")
    if args.json:
        (root / args.json).write_text(
            json.dumps(asdict(ui), ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
    print(text)
    incomplete = [
        item for item in ui.slots
        if not item.native or not item.hosted or (args.companion and not item.implemented)
    ]
    if args.check and incomplete:
        print("plugin UI coverage check FAILED", file=sys.stderr)
        return 1
    print("plugin UI coverage check OK", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
