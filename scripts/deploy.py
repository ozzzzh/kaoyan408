#!/usr/bin/env python3
"""
一键发版：本地构建前端 → 上传 dist → 切换 → 重建容器 → 校验。

为什么需要它：生产机的 Node 是 v20.11（跑不了 vite 8），前端必须在本地构建后上传；
每次手动拼 tar/scp/ssh/mv 容易漏步骤（少一个 --force-recreate web，页面就还是旧的）。

前置：
  - 本机 Python 3 + paramiko（pip install paramiko）
  - ~/.ssh/config_metadata.json 里已有 ipeagent-server 的连接信息
    （或通过环境变量 DEPLOY_HOST / DEPLOY_PORT / DEPLOY_USER / DEPLOY_PASSWORD 提供）

用法：
  python scripts/deploy.py                 # 只发前端（构建 + 上传 + 重建 web）
  python scripts/deploy.py --api           # 顺带重建后端镜像（改了 Java / 迁移时用）
  python scripts/deploy.py --skip-build    # 复用本地已有 client/dist
  python scripts/deploy.py --patch x.patch # 先把补丁打到服务器仓库并提交，再构建后端
  python scripts/deploy.py --base https://your.domain   # 换校验地址
"""
from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import tarfile
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CLIENT = ROOT / "client"
DIST = CLIENT / "dist"
PROJECT_REMOTE = "/mnt/workspace/kaoyan408"
COMPOSE = "docker compose -p kaoyan408"
DEFAULT_BASE = "https://tizhilian408.de5.net"


def log(message: str) -> None:
    print(f"[deploy] {message}", flush=True)


def _port_from_ssh_config(alias: str) -> int | None:
    """从 ~/.ssh/config 读取该别名的 Port/User（优先于 metadata）。

    实测踩过：config_metadata.json 里写的端口是 22，而 ssh config 里是 17080，
    照 metadata 连会得到 "Authentication failed"（22 端口是另一套服务）。
    """
    cfg = Path.home() / ".ssh" / "config"
    if not cfg.exists():
        return None
    port = None
    in_block = False
    for raw in cfg.read_text(encoding="utf-8", errors="replace").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        lower = line.lower()
        if lower.startswith("host "):
            names = line.split()[1:]
            in_block = alias in names
            continue
        if in_block and lower.startswith("port "):
            try:
                port = int(line.split()[1])
            except (IndexError, ValueError):
                port = None
    return port


def load_target() -> dict:
    host = os.environ.get("DEPLOY_HOST")
    if host:
        return {
            "host": host,
            "port": int(os.environ.get("DEPLOY_PORT", "22")),
            "user": os.environ.get("DEPLOY_USER", "root"),
            "password": os.environ.get("DEPLOY_PASSWORD", ""),
        }
    meta_path = Path.home() / ".ssh" / "config_metadata.json"
    if not meta_path.exists():
        sys.exit("找不到 SSH 连接信息：请设置 DEPLOY_HOST 等环境变量，或准备 ~/.ssh/config_metadata.json")
    with meta_path.open(encoding="utf-8") as fh:
        meta = json.load(fh)
    target = dict(meta.get("ipeagent-server") or {})
    if not target:
        sys.exit("config_metadata.json 里没有 ipeagent-server 条目")
    override_port = _port_from_ssh_config("ipeagent-server")
    if override_port:
        target["port"] = override_port
    return target


def build_frontend() -> None:
    log("本地构建前端（含 vue-tsc 类型检查）…")
    subprocess.run(["npm", "run", "build"], cwd=CLIENT, check=True, shell=os.name == "nt")


def pack_dist() -> Path:
    archive = Path(os.environ.get("TEMP", "/tmp")) / "kaoyan-dist-deploy.tgz"
    if archive.exists():
        archive.unlink()
    log(f"打包 dist → {archive.name}")
    with tarfile.open(archive, "w:gz") as tar:
        tar.add(DIST, arcname="dist")
    size_kb = archive.stat().st_size / 1024
    log(f"包大小 {size_kb:.0f} KB")
    return archive


class Remote:
    def __init__(self, target: dict):
        import paramiko  # 延迟导入：没装依赖时给出清晰提示

        self.client = paramiko.SSHClient()
        self.client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
        self.client.connect(
            target["host"],
            port=int(target.get("port", 22)),
            username=target.get("user", "root"),
            password=target.get("password"),
            timeout=int(target.get("timeout", 30)),
            allow_agent=False,
            look_for_keys=False,
        )
        self.sftp = self.client.open_sftp()

    def run(self, command: str, timeout: int = 1800, quiet: bool = False) -> tuple[int, str]:
        _, out, err = self.client.exec_command(command, timeout=timeout)
        stdout = out.read().decode("utf-8", "replace").strip()
        stderr = err.read().decode("utf-8", "replace").strip()
        code = out.channel.recv_exit_status()
        if not quiet:
            if stdout:
                print(stdout[-4000:])
            if stderr:
                print(f"[stderr] {stderr[-800:]}")
        return code, stdout

    def upload(self, local: Path, remote: str) -> None:
        self.sftp.put(str(local), remote)

    def close(self) -> None:
        try:
            self.sftp.close()
        finally:
            self.client.close()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--api", action="store_true", help="顺带重建后端镜像")
    parser.add_argument("--skip-build", action="store_true", help="复用已有 client/dist")
    parser.add_argument("--patch", help="先应用这个补丁到服务器仓库并提交")
    parser.add_argument("--base", default=DEFAULT_BASE, help="发版后校验用的地址")
    args = parser.parse_args()

    if not args.skip_build:
        build_frontend()
    if not (DIST / "index.html").exists():
        sys.exit("client/dist 不存在，先跑一次构建")

    entry = None
    for line in (DIST / "index.html").read_text(encoding="utf-8").splitlines():
        if 'assets/index-' in line:
            entry = line.strip()
            break
    log(f"本地入口：{entry or '(未识别)'}")

    archive = pack_dist()
    remote = Remote(load_target())
    try:
        if args.patch:
            patch_path = Path(args.patch).resolve()
            log(f"上传补丁 {patch_path.name} 并应用到服务器仓库")
            remote.upload(patch_path, "/tmp/kaoyan-deploy.patch")
            code, _ = remote.run(
                f"cd {PROJECT_REMOTE} && git apply /tmp/kaoyan-deploy.patch && git add -A && "
                f"git -c user.name=kaoyan408-deploy -c user.email=deploy@kaoyan408.local "
                f"commit -q -m 'deploy: 同步本地改动' && git log --oneline -1"
            )
            if code != 0:
                return 1

        log("上传 dist")
        remote.upload(archive, "/tmp/kaoyan-dist-deploy.tgz")

        log("切换 dist（保留上一版，便于回滚）")
        code, _ = remote.run(
            "set -e; cd %s/client; TS=$(date +%%Y%%m%%d-%%H%%M%%S); "
            "rm -rf dist.new; mkdir -p dist.new; "
            "tar -xzf /tmp/kaoyan-dist-deploy.tgz -C dist.new --strip-components=1; "
            "test -f dist.new/index.html; test -f dist.new/sw.js; "
            "test -d dist && mv dist dist.prev.$TS || true; "
            "mv dist.new dist; chmod -R a+rX dist; "
            "ls -d dist.prev.* 2>/dev/null | sort | head -n -2 | xargs -r rm -rf; "
            "grep -o 'assets/index-[A-Za-z0-9_-]*\\.js' dist/index.html | head -1" % PROJECT_REMOTE
        )
        if code != 0:
            return 1

        log("重建 web 容器（bind mount 持有旧 inode，必须 --force-recreate）")
        code, _ = remote.run(f"cd {PROJECT_REMOTE} && {COMPOSE} up -d --force-recreate web 2>&1 | tail -3")
        if code != 0:
            return 1

        if args.api:
            log("重建 api 容器（会执行 Flyway 迁移）")
            code, _ = remote.run(f"cd {PROJECT_REMOTE} && {COMPOSE} up -d --build api 2>&1 | tail -4")
            if code != 0:
                return 1

        log("健康检查")
        deadline = time.time() + 120
        healthy = False
        while time.time() < deadline:
            code, body = remote.run(f"curl -s -m 5 http://127.0.0.1:17073/api/v1/health", quiet=True)
            if code == 0 and '"status":"UP"' in body:
                print(body)
                healthy = True
                break
            time.sleep(3)
        if not healthy:
            log("健康检查未通过，请查日志：docker compose -p kaoyan408 logs --tail 100 api")
            return 1

        log(f"公网校验 {args.base}")
        code, body = remote.run(
            f"curl -s -m 15 -o /dev/null -w '%{{http_code}}' {args.base}/", quiet=True
        )
        log(f"首页 HTTP {body}")
    finally:
        remote.close()

    log("完成。回滚：把 dist.prev.* 换回 dist，然后 up -d --force-recreate web")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
