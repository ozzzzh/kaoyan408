#!/usr/bin/env python3
"""服务器部署锁：多会话并行时的互斥开关。

为什么需要：两个会话都能对同一台机器执行发版（git 同步 + 重建容器 + 切换 dist），
同时跑就会出现「A 正在重建 api，B 把 dist 换了」这种半成品状态，而且事后很难判断
线上到底是哪一版。锁文件放在项目目录里，谁发版谁先占坑。

用法：
    python scripts/deploy-lock.py status     # 看锁（谁在发、多久了）
    python scripts/deploy-lock.py acquire    # 占锁（默认 15 分钟；被占则非零退出）
    python scripts/deploy-lock.py release    # 释放
    python scripts/deploy-lock.py acquire --ttl 600 --owner alice

约定（写进 COLLAB-RULES）：
    发版前 acquire，发版后（无论成功失败）release；
    acquire 失败说明另一个会话正在发版，等它结束，不要抢。

锁是「软锁」：超过 ttl 视为过期，后来的 acquire 会接管并打印提示。
这样可以避免某个会话崩溃后把部署永久锁死。
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path

try:
    import paramiko
except ImportError:  # pragma: no cover
    sys.exit("需要 paramiko：pip install paramiko")

PROJECT = "/mnt/workspace/kaoyan408"
LOCK = f"{PROJECT}/.deploy.lock"
DEFAULT_TTL = 900  # 15 分钟


def _port_from_ssh_config(alias: str) -> int | None:
    """ssh 配置里的 Port 优先于 metadata（metadata 可能留着旧端口）。"""
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
            in_block = alias in line.split()[1:]
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
        target = dict(json.load(fh).get("ipeagent-server") or {})
    if not target:
        sys.exit("config_metadata.json 里没有 ipeagent-server 条目")
    override = _port_from_ssh_config("ipeagent-server")
    if override:
        target["port"] = override
    return target


def run(command: str, timeout: int = 60) -> tuple[int, str]:
    t = load_target()
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(t["host"], port=int(t["port"]), username=t["user"],
                   password=t["password"], timeout=20, allow_agent=False, look_for_keys=False)
    try:
        _, stdout, stderr = client.exec_command(command, timeout=timeout)
        out = stdout.read().decode("utf-8", "replace")
        err = stderr.read().decode("utf-8", "replace")
        code = stdout.channel.recv_exit_status()
    finally:
        client.close()
    return code, (out + err).strip()


def status() -> int:
    code, out = run(
        f"cd {PROJECT} 2>/dev/null || exit 3; "
        f"if [ -f .deploy.lock ]; then echo 'LOCKED'; cat .deploy.lock; "
        "echo \"age=$(( $(date +%s) - $(stat -c %Y .deploy.lock) ))s\"; else echo 'FREE'; fi"
    )
    if code != 0:
        print(f"读取失败（{code}）：{out}")
        return code
    print(out)
    return 0


def acquire(ttl: int, owner: str) -> int:
    payload = f"owner={owner}\nttl={ttl}s\n"
    code, out = run(
        f"cd {PROJECT} || exit 3; "
        "if [ -f .deploy.lock ]; then "
        "  AGE=$(( $(date +%s) - $(stat -c %Y .deploy.lock) )); "
        f"  if [ $AGE -lt {ttl} ]; then echo \"LOCKED age=${{AGE}}s\"; cat .deploy.lock; exit 9; fi; "
        "  echo '(过期锁，接管)'; fi; "
        # 时间戳要在远端展开：写进 printf 的格式串会原样留下 $(date -Is)
        f"{{ printf '{payload}'; printf 'at=%s\\n' \"$(date -Is)\"; }} > .deploy.lock "
        "&& echo ACQUIRED && cat .deploy.lock"
    )
    print(out)
    return code


def release() -> int:
    code, out = run(f"cd {PROJECT} || exit 3; rm -f .deploy.lock && echo RELEASED")
    print(out)
    return code


def main() -> int:
    parser = argparse.ArgumentParser(description="服务器部署锁")
    parser.add_argument("action", choices=["status", "acquire", "release"])
    parser.add_argument("--ttl", type=int, default=DEFAULT_TTL, help="锁有效期（秒），默认 900")
    parser.add_argument("--owner", default=os.environ.get("DEPLOY_OWNER", "unknown"),
                        help="占锁者标识（建议填会话名，便于排查）")
    args = parser.parse_args()

    if args.action == "status":
        return status()
    if args.action == "acquire":
        return acquire(args.ttl, args.owner)
    return release()


if __name__ == "__main__":
    sys.exit(main())
