#!/usr/bin/env bash
# 在本机 Terminal（已登录 dreamina、可访问 macOS Keychain）运行完整三阶段 exec 采集。
# Cursor Agent 沙箱常因 keyring 失败导致 user_credit exit 1；请在本脚本中完成 Phase A/B/C。
#
# 用法:
#   chmod +x scripts/dreamina-cli-audit-interactive.sh
#   ./scripts/dreamina-cli-audit-interactive.sh
#
# 可选环境变量:
#   DREAMINA_AUDIT_POLL=1              text2image 采集 poll 全流程（默认 1）
#   DREAMINA_AUDIT_POLL_MAX_SEC=120    poll 最长等待秒数
#   DREAMINA_AUDIT_SAMPLE_IMAGE=path   image 类命令样例图
#
# 不会执行 logout / relogin / login。

set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export AUDIT_DIR="${AUDIT_DIR:-$ROOT/.cli-audit}"
export DREAMINA_CLI_EXECUTABLE="${DREAMINA_CLI_EXECUTABLE:-dreamina}"
export DREAMINA_AUDIT_POLL="${DREAMINA_AUDIT_POLL:-1}"
export DREAMINA_AUDIT_POLL_MAX_SEC="${DREAMINA_AUDIT_POLL_MAX_SEC:-120}"
export HOME="${HOME:?HOME must be set for OAuth/keyring}"
export PATH="${HOME}/.local/bin:/usr/local/bin:/usr/bin:/bin:${PATH}"

echo "=== Dreamina CLI interactive audit ==="
echo "AUDIT_DIR=$AUDIT_DIR"
echo "DREAMINA=$DREAMINA_CLI_EXECUTABLE"
echo "HOME=$HOME"
echo "DREAMINA_AUDIT_POLL=$DREAMINA_AUDIT_POLL"
echo "DREAMINA_AUDIT_POLL_MAX_SEC=$DREAMINA_AUDIT_POLL_MAX_SEC"
echo ""
echo "Pre-check: dreamina user_credit (must exit 0 with JSON)"
if ! "$DREAMINA_CLI_EXECUTABLE" user_credit >/dev/null 2>&1; then
  echo "ERROR: user_credit failed. Please run 'dreamina login' in this Terminal first."
  echo "       (Agent shells cannot access macOS keyring — use your local Terminal.)"
  exit 1
fi
echo "Logged in OK."
echo ""
exec "$ROOT/scripts/dreamina-cli-audit.sh"
