#!/usr/bin/env python3
"""APK 发版守卫：保证 APK 引用的 bridge / rootfs 版本已经真正发布。

发布链条（详见仓库根目录 RELEASING.md）：

    bridge 插件发版  ->  rootfs 镜像重建  ->  APK 版本提升并打 tag

APK 的 tag 是最后一步，但它引用了两样外部产物：

  - `app/src/main/assets/bridge-plugin.json` 的 `version`：必须已经存在
    对应 `v<version>` 的 bridge release，否则设备无法在设置页更新插件；
  - `app/src/main/assets/rootfs-manifest.json` 的 `releaseTag`：必须已经存在
    对应 rootfs release，否则内置兜底清单会指向一个不存在的下载地址。

本脚本在 tag 构建时运行：

  1. 校验两个文件里的 bridge 版本一致；
  2. 校验 bridge release 存在；缺失时，如果配置了 BRIDGE_DISPATCH_TOKEN，
     触发 bridge 仓库的 release workflow 并等待它出现；没有 token 直接失败；
  3. 校验 rootfs release 存在；
  4. 如果 bridge 仓库已有比 APK 解包版本更新的 release，只警告（运行时会提示
     用户更新），不阻塞 APK 发布。

环境变量：

  BRIDGE_DISPATCH_TOKEN  可选。对 bridge 仓库有 Actions/Contents 写权限的
                         token；用于 APK 发版时自动补发 bridge release。
"""

from __future__ import annotations

import json
import os
import re
import sys
import time
import urllib.error
import urllib.request

API = "https://api.github.com"
USER_AGENT = "dsh-mobile-release-guard"
BRIDGE_WORKFLOW = "release.yml"
POLL_ATTEMPTS = 60
POLL_INTERVAL_SECONDS = 10


def fail(message: str) -> None:
    print(f"::error::{message}", file=sys.stderr)
    raise SystemExit(1)


def warn(message: str) -> None:
    print(f"::warning::{message}")


def load_json(path: str) -> dict:
    with open(path, encoding="utf-8") as stream:
        return json.load(stream)


def request(path: str, token: str | None, method: str = "GET", body: dict | None = None) -> tuple[int, dict | None]:
    headers = {
        "Accept": "application/vnd.github+json",
        "User-Agent": USER_AGENT,
        "X-GitHub-Api-Version": "2022-11-28",
    }
    if token:
        headers["Authorization"] = f"Bearer {token}"
    data = None
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(API + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            raw = response.read().decode("utf-8")
            return response.status, (json.loads(raw) if raw else None)
    except urllib.error.HTTPError as error:
        raw = error.read().decode("utf-8", "replace")
        payload = None
        try:
            payload = json.loads(raw) if raw else None
        except json.JSONDecodeError:
            pass
        return error.code, payload


def release_exists(repo: str, tag: str, token: str | None) -> bool:
    status, _ = request(f"/repos/{repo}/releases/tags/{tag}", token)
    if status == 200:
        return True
    if status == 404:
        return False
    fail(f"查询 {repo} 的 release {tag} 失败：HTTP {status}")


def latest_release_tag(repo: str, token: str | None) -> str | None:
    status, payload = request(f"/repos/{repo}/releases/latest", token)
    if status == 404:
        return None
    if status != 200 or not isinstance(payload, dict):
        return None
    tag = payload.get("tag_name")
    return tag if isinstance(tag, str) and tag else None


def repo_slug_from_url(url: str) -> str:
    match = re.search(r"github\.com/([^/]+)/([^/#]+)", url)
    if not match:
        fail(f"无法从 URL 解析 GitHub 仓库：{url}")
    return f"{match.group(1)}/{match.group(2)}"


def dispatch_bridge_release(repo: str, version: str, token: str) -> None:
    status, payload = request(
        f"/repos/{repo}/actions/workflows/{BRIDGE_WORKFLOW}/dispatches",
        token,
        method="POST",
        body={"ref": "master", "inputs": {"version": version}},
    )
    if status != 204:
        detail = payload.get("message") if isinstance(payload, dict) else None
        fail(f"触发 bridge 发版失败：HTTP {status}{f'（{detail}）' if detail else ''}")


def main() -> None:
    bridge = load_json("app/src/main/assets/bridge-plugin.json")
    rootfs = load_json("app/src/main/assets/rootfs-manifest.json")

    version = str(bridge.get("version", "")).strip()
    if not re.fullmatch(r"\d+\.\d+\.\d+", version):
        fail(f"bridge-plugin.json 的 version 不是 semver：{version!r}")

    bridge_version = str(rootfs.get("bridgeVersion", "")).strip()
    if bridge_version != version:
        fail(
            "版本不一致：bridge-plugin.json 说 "
            f"{version}，rootfs-manifest.json 的 bridgeVersion 说 {bridge_version}。"
            "先更新 rootfs 清单或 bridge 版本，再发 APK。"
        )

    bridge_repo = repo_slug_from_url(str(bridge.get("repository", "")))
    rootfs_repo = repo_slug_from_url(str(rootfs.get("downloadUrls", {}).get("arm64-v8a", "")))
    rootfs_tag = str(rootfs.get("releaseTag", "")).strip()
    if not rootfs_tag:
        fail("rootfs-manifest.json 缺少 releaseTag")

    token = os.environ.get("BRIDGE_DISPATCH_TOKEN", "").strip() or None
    bridge_tag = f"v{version}"

    print(f"bridge 版本 {version}（{bridge_repo}，tag {bridge_tag}）")
    print(f"rootfs 版本 {rootfs_tag}（{rootfs_repo}）")

    if not release_exists(bridge_repo, bridge_tag, token):
        if token is None:
            fail(
                f"bridge release {bridge_tag} 不存在，且未配置 BRIDGE_DISPATCH_TOKEN。"
                "先把 bridge 插件发版（package.json 提升 version、release=true 并推送），再发 APK。"
            )
        print(f"bridge release {bridge_tag} 不存在，触发 bridge 仓库的 {BRIDGE_WORKFLOW} ...")
        dispatch_bridge_release(bridge_repo, version, token)
        for attempt in range(POLL_ATTEMPTS):
            time.sleep(POLL_INTERVAL_SECONDS)
            if release_exists(bridge_repo, bridge_tag, token):
                print(f"bridge release {bridge_tag} 已出现（等待 {attempt + 1} 轮）")
                break
        else:
            fail(f"等待 bridge release {bridge_tag} 超时（{POLL_ATTEMPTS * POLL_INTERVAL_SECONDS}s）")

    if not release_exists(rootfs_repo, rootfs_tag, token):
        fail(
            f"rootfs release {rootfs_tag} 不存在：内置兜底清单会指向失效地址。"
            "先等 rootfs 镜像发布，再更新 rootfs-manifest.json 后重新打 tag。"
        )

    latest_bridge = latest_release_tag(bridge_repo, token)
    if latest_bridge and latest_bridge != bridge_tag:
        warn(
            f"bridge 仓库最新 release 是 {latest_bridge}，APK 内置版本是 {bridge_tag}。"
            "运行时会提示用户更新插件；如需内置到新镜像，提升 bridge-plugin.json 后重发 APK。"
        )

    print("APK 引用的 bridge / rootfs 版本均已发布，继续构建。")


if __name__ == "__main__":
    main()
