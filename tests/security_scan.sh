#!/usr/bin/env bash
# PowerMeter 安全 / PRD 静态扫描（bash 包装，便于单独执行或被 build.sh 调用）。
# 用法：bash tests/security_scan.sh [项目根目录]
# 退出码：0 全通过；非 0 有失败。
set -uo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="${1:-$(cd "$HERE/.." && pwd)}"

if ! command -v python3 >/dev/null 2>&1; then
  echo "FAIL 扫描环境：python3 不可用，无法执行静态扫描"
  echo "==== 静态扫描结果：通过 0，失败 1 ===="
  exit 1
fi

python3 "$HERE/security_scan.py" "$ROOT"
