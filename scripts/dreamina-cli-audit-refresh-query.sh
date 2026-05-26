#!/usr/bin/env bash
# 从 .cli-audit 提取 submit_id，刷新 query_result / list_task / user_credit。
# 用法: ./scripts/dreamina-cli-audit-refresh-query.sh
# 约束: 不执行 login / logout / relogin。

set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
AUDIT_DIR="${AUDIT_DIR:-$ROOT/.cli-audit}"
DREAMINA="${DREAMINA_CLI_EXECUTABLE:-dreamina}"
export HOME="${HOME:?HOME must be set for OAuth/keyring}"
export PATH="${HOME}/.local/bin:/usr/local/bin:/usr/bin:/bin:${PATH:-}"

mkdir -p "$AUDIT_DIR"
cd "$ROOT"

exec_capture_verbose() {
  local name="$1"
  shift
  local out="$AUDIT_DIR/exec_${name}.txt"
  local stdout_file stderr_file ec=0 start_ts end_ts
  start_ts="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  stdout_file="$(mktemp)"
  stderr_file="$(mktemp)"
  "$@" >"$stdout_file" 2>"$stderr_file" || ec=$?
  end_ts="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  {
    echo "=== CMD: $* ==="
    echo "=== START: $start_ts ==="
    echo "=== END: $end_ts ==="
    echo "=== STDOUT (raw, untruncated) ==="
    cat "$stdout_file"
    echo "=== STDERR (raw, untruncated) ==="
    cat "$stderr_file"
    echo "=== STDOUT_BYTES: $(wc -c <"$stdout_file" | tr -d ' ') ==="
    echo "=== STDERR_BYTES: $(wc -c <"$stderr_file" | tr -d ' ') ==="
    echo "=== EXIT: $ec ==="
  } >"$out"
  rm -f "$stdout_file" "$stderr_file"
  echo "ExecVerbose: exec_${name}.txt (exit=$ec)"
}

collect_submit_ids() {
  python3 - <<'PY'
import pathlib, re, json

root = pathlib.Path(".cli-audit")
patterns = [
    "exec_*submit*.txt",
    "exec_query_result*.txt",
    "LOGIN_STATUS.txt",
]
ids = set()
uuid_re = re.compile(
    r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", re.I
)
fake = "00000000-0000-0000-0000-000000000000"

for pat in patterns:
    for path in sorted(root.glob(pat)):
        text = path.read_text(encoding="utf-8", errors="replace")
        for m in re.finditer(r'"submit_id"\s*:\s*"([^"]+)"', text):
            sid = m.group(1).strip()
            if sid and sid != fake:
                ids.add(sid)
        for m in re.finditer(r"submit[_-]id[=:]([0-9a-f-]{36})", text, re.I):
            sid = m.group(1)
            if sid != fake:
                ids.add(sid)
        for m in uuid_re.finditer(text):
            sid = m.group(0)
            if sid != fake and "submit" in text.lower():
                ids.add(sid)

for sid in sorted(ids):
    print(sid)
PY
}

short_id() {
  local full="$1"
  echo "${full%%-*}"
}

echo "=== dreamina-cli-audit-refresh-query ==="
SUBMIT_IDS=()
while IFS= read -r sid; do
  [ -n "$sid" ] && SUBMIT_IDS+=("$sid")
done < <(collect_submit_ids)
if [ "${#SUBMIT_IDS[@]}" -eq 0 ]; then
  echo "No submit_id found under $AUDIT_DIR"
  exit 1
fi
echo "Found ${#SUBMIT_IDS[@]} submit_id(s)"

for sid in "${SUBMIT_IDS[@]}"; do
  short="$(short_id "$sid")"
  exec_capture_verbose "query_refresh_${short}" \
    "$DREAMINA" query_result "--submit_id=${sid}"
done

exec_capture_verbose "list_task_refresh" "$DREAMINA" list_task --limit 10
exec_capture_verbose "user_credit_refresh" "$DREAMINA" user_credit

echo "Done. Outputs: $AUDIT_DIR/exec_query_refresh_*.txt, exec_list_task_refresh.txt, exec_user_credit_refresh.txt"
