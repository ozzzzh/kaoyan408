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
import re
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
ENTRY_RE = re.compile(r"assets/index-[A-Za-z0-9_-]+\.js")
LOCK_SCRIPT = ROOT / "scripts" / "deploy-lock.py"


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


def acquire_lock(owner: str) -> bool:
    """拿服务器部署锁。拿不到说明别人正在发版，直接退出而不是硬上。"""
    if not LOCK_SCRIPT.exists():
        log("未找到 scripts/deploy-lock.py，跳过部署锁（不建议并发发版）")
        return True
    result = subprocess.run(
        [sys.executable, str(LOCK_SCRIPT), "acquire", "--owner", owner],
        capture_output=True, text=True,
    )
    out = (result.stdout or "") + (result.stderr or "")
    print(out.strip())
    if result.returncode != 0 or "ACQUIRED" not in out:
        log("部署被锁：另一个会话正在发版。等它结束再试（15 分钟后锁会自动过期）")
        return False
    return True


def release_lock() -> None:
    if LOCK_SCRIPT.exists():
        subprocess.run([sys.executable, str(LOCK_SCRIPT), "release"],
                       capture_output=True, text=True)


def check_not_behind_main(allow_behind: bool) -> bool:
    """构建前确认自己的分支包含 main。

    为什么必须查：用自己的分支构建 dist 会缺主线的新功能，发上去就把别人的功能盖掉了
    （2026-11-16 线上前端被互相覆盖两次，就是这个原因）。
    """
    try:
        behind = subprocess.run(
            ["git", "rev-list", "--count", "HEAD..main"],
            cwd=ROOT, capture_output=True, text=True,
        )
        if behind.returncode != 0:
            return True  # 不在 git 仓库里（比如打包下载的源码），不做限制
        count = int((behind.stdout or "0").strip() or "0")
    except Exception:
        return True
    if count == 0:
        return True
    log(f"当前分支落后 main {count} 个提交")
    if allow_behind:
        log("已用 --allow-behind 跳过；请确认你知道这会缺主线功能")
        return True
    log("先执行 `git merge main` 再发版（或用 --allow-behind 显式跳过）")
    return False


def entry_of(html: str) -> str | None:
    match = ENTRY_RE.search(html or "")
    return match.group(0) if match else None


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


def warm_up(base: str, rounds: int = 2) -> int:
    """把入口页面引用的资源在公网入口各取一次，让边缘缓存提前填充。

    为什么需要：新构建的 chunk 文件名带新哈希，对 Cloudflare 来说全是 MISS。
    第一个真实访客会替所有人「付」这轮回源（每个 chunk 200ms–1.6s）。
    部署脚本自己先取一遍，就能把这个成本挪到部署阶段。
    """
    import re
    from urllib.request import Request, urlopen

    try:
        with urlopen(Request(base + "/", headers={"User-Agent": "kaoyan-deploy-warmup"}), timeout=20) as resp:
            html = resp.read().decode("utf-8", "replace")
    except Exception as error:  # 预热失败不影响发版结果
        log(f"预热跳过：取入口失败 {error}")
        return 0

    refs = sorted(set(re.findall(r'(?:src|href)="(\.?/?assets/[^"]+)"', html)))
    refs += ["theme-init.js", "sw.js"]
    ok = 0
    for _ in range(rounds):
        for ref in refs:
            url = base + "/" + ref.lstrip("./")
            try:
                with urlopen(Request(url, headers={"User-Agent": "kaoyan-deploy-warmup"}), timeout=20) as resp:
                    resp.read(1024)
                    if resp.status == 200:
                        ok += 1
            except Exception:
                pass
    log(f"预热完成：{len(refs)} 个资源 × {rounds} 轮")
    return ok


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
    parser.add_argument("--allow-behind", action="store_true",
                        help="允许在落后 main 的分支上发版（默认拒绝）")
    parser.add_argument("--owner", default=os.environ.get("DEPLOY_OWNER", "unknown"),
                        help="占锁者标识，写进 .deploy.lock 便于排查")
    args = parser.parse_args()

    # 多会话都能发版，但必须串行：锁没拿到就直接退出，绝不并发写服务器
    if not acquire_lock(args.owner):
        return 1
    try:
        return run_deploy(args)
    finally:
        release_lock()


def run_deploy(args) -> int:
    if not check_not_behind_main(args.allow_behind):
        return 1

    if not args.skip_build:
        build_frontend()
    if not (DIST / "index.html").exists():
        sys.exit("client/dist 不存在，先跑一次构建")

    entry = entry_of((DIST / "index.html").read_text(encoding="utf-8"))
    log(f"本地入口：{entry or '(未识别)'}")
    if not entry:
        log("无法从 dist/index.html 解析入口文件名，停止发版")
        return 1

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
        # 关键：合并 assets 而不是整包替换。
        # 旧哈希 chunk 必须留着 —— 浏览器里的 Service Worker 可能还缓存着旧外壳，
        # 它引用的正是这些旧文件名；一删就 404，表现为「某个菜单点了没反应」。
        code, _ = remote.run(
            "set -e; cd %s/client; TS=$(date +%%Y%%m%%d-%%H%%M%%S); "
            "rm -rf dist.new; mkdir -p dist.new; "
            "tar -xzf /tmp/kaoyan-dist-deploy.tgz -C dist.new --strip-components=1; "
            "test -f dist.new/index.html; test -f dist.new/sw.js; "
            "test -d dist && cp -a dist dist.prev.$TS || true; "
            "mkdir -p dist/assets; "
            "cp -a dist.new/assets/. dist/assets/; "
            "cp -a dist.new/index.html dist/new-index.html; "
            "for f in $(cd dist.new && ls -1 | grep -v '^assets$'); do cp -a \"dist.new/$f\" \"dist/$f\"; done; "
            "rm -f dist/new-index.html; chmod -R a+rX dist; "
            # 只清理 30 天前的旧 chunk，避免目录无限膨胀（保留最近若干代给旧客户端）
            "find dist/assets -maxdepth 1 -type f -mtime +30 -delete 2>/dev/null || true; "
            "ls -d dist.prev.* 2>/dev/null | sort | head -n -2 | xargs -r rm -rf; "
            "echo -n '当前 entry: '; grep -o 'assets/index-[A-Za-z0-9_-]*\\.js' dist/index.html | head -1; "
            "echo -n 'assets 文件数: '; ls dist/assets | wc -l" % PROJECT_REMOTE
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

        log("边缘预热：把新构建的 chunk 在公网入口各取一次")
        warmed = warm_up(args.base)

        log(f"公网校验 {args.base}")
        code, body = remote.run(
            f"curl -s -m 15 -o /dev/null -w '%{{http_code}}' {args.base}/", quiet=True
        )
        log(f"首页 HTTP {body}（预热 {warmed} 个资源）")

        # 规则 6.3 第 5 条：公网返回的入口必须就是这次构建的那个。
        # 边缘缓存 entry HTML 60 秒，所以带随机参数重试，最多等 2 分钟。
        matched = False
        deadline = time.time() + 120
        public_entry = None
        while time.time() < deadline:
            try:
                from urllib.request import Request, urlopen
                url = f"{args.base}/?warmup={int(time.time() * 1000)}"
                with urlopen(Request(url, headers={"User-Agent": "kaoyan-deploy-check",
                                                   "Cache-Control": "no-cache"}), timeout=20) as resp:
                    public_entry = entry_of(resp.read().decode("utf-8", "replace"))
            except Exception as error:
                log(f"取公网入口失败（稍后重试）：{error}")
                public_entry = None
            if public_entry == entry:
                matched = True
                break
            time.sleep(5)
        if matched:
            log(f"公网入口一致：{public_entry}")
        else:
            log(f"公网入口不一致：本地 {entry} / 公网 {public_entry}")
            log("可能原因：另一个会话同时发版覆盖了 dist，或边缘缓存未过期。请查后再决定是否重发。")
            return 1
    finally:
        remote.close()

    log("完成。回滚：把 dist.prev.* 换回 dist，然后 up -d --force-recreate web")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
