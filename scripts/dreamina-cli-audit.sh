#!/usr/bin/env bash
# 本地采集 dreamina 全量命令 / -h 帮助输出，并按三阶段执行只读/生成类命令。
# 用法: ./scripts/dreamina-cli-audit.sh
# 已登录环境推荐: ./scripts/dreamina-cli-audit-interactive.sh
# 说明: 官方帮助为 `dreamina <cmd> -h` 或 `dreamina help <cmd>`；二级如 `dreamina session list -h`。
# 不会执行 logout / relogin / login。
#
# 生成阶段环境变量:
#   DREAMINA_AUDIT_POLL=1|0          是否对 text2image 做 --poll 采集（默认 1）
#   DREAMINA_AUDIT_POLL_MAX_SEC=120  text2image poll 最长秒数（默认 120，1s 间隔）
#   DREAMINA_AUDIT_SAMPLE_IMAGE=path 本地样例图（默认 scripts/fixtures/audit-sample.png）
#   DREAMINA_AUDIT_SUBMIT_ID=uuid    覆盖 query_result 用的历史 submit_id

set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
AUDIT_DIR="${AUDIT_DIR:-$ROOT/.cli-audit}"
DREAMINA="${DREAMINA_CLI_EXECUTABLE:-dreamina}"
POLL_ENABLED="${DREAMINA_AUDIT_POLL:-1}"
POLL_MAX_SEC="${DREAMINA_AUDIT_POLL_MAX_SEC:-120}"
export HOME="${HOME:?HOME must be set for OAuth/keyring}"
export PATH="${HOME}/.local/bin:/usr/local/bin:/usr/bin:/bin:${PATH:-}"

mkdir -p "$AUDIT_DIR"
cd "$ROOT"

run_cmd() {
  local name="$1"
  shift
  local out="$AUDIT_DIR/${name}.txt"
  {
    echo "=== CMD: $* ==="
    "$@" 2>&1 || true
    echo ""
    echo "=== EXIT_CODE: $? ==="
  } >"$out"
  echo "Wrote $out"
}

# 真实执行：分离 stdout / stderr（供 SDK 对齐）
exec_capture() {
  exec_capture_verbose "$@"
}

# 完整保留 stdout/stderr，不做截断；记录时间与字节数
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
  return 0
}

# 跳过某生成命令时写入说明文件
write_skip_note() {
  local name="$1"
  local reason="$2"
  local out="$AUDIT_DIR/exec_${name}.txt"
  {
    echo "=== SKIPPED ==="
    echo "reason=$reason"
    echo "hint=Set DREAMINA_AUDIT_SAMPLE_IMAGE or add scripts/fixtures/audit-sample.png"
  } >"$out"
  echo "Skip: exec_${name}.txt ($reason)"
}

# 解析 exec 文件中的 submit JSON
extract_submit_id_from_exec() {
  local exec_name="$1"
  if ! command -v python3 >/dev/null 2>&1; then
    return 1
  fi
  EXEC_NAME="$exec_name" python3 - <<'PY'
import json, pathlib, re, os
name = os.environ.get("EXEC_NAME", "")
p = pathlib.Path(".cli-audit") / f"exec_{name}.txt"
if not p.exists():
    raise SystemExit
text = p.read_text(encoding="utf-8", errors="replace")
m = re.search(r"\{", text)
if not m:
    raise SystemExit
end = text.rfind("}")
if end < m.start():
    raise SystemExit
try:
    obj = json.loads(text[m.start() : end + 1])
except json.JSONDecodeError:
    raise SystemExit
sid = obj.get("submit_id")
if sid:
    print(sid)
PY
}

# 从 list_task JSON 提取首条 submit_id（扫描 exec_* / list_task_*.txt）
extract_first_submit_id() {
  if [[ -n "${DREAMINA_AUDIT_SUBMIT_ID:-}" ]]; then
    echo "$DREAMINA_AUDIT_SUBMIT_ID"
    return 0
  fi
  if ! command -v python3 >/dev/null 2>&1; then
    return 1
  fi
  python3 - <<'PY'
import json, pathlib, re
root = pathlib.Path(".cli-audit")
candidates = sorted(root.glob("exec_list_task*.txt")) + sorted(root.glob("list_task*.txt"))
for p in candidates:
    if not p.exists():
        continue
    text = p.read_text(encoding="utf-8", errors="replace")
    m = re.search(r"\[\s*\{", text)
    if not m:
        continue
    end = text.rfind("]")
    if end < m.start():
        continue
    try:
        arr = json.loads(text[m.start() : end + 1])
    except json.JSONDecodeError:
        continue
    if arr and isinstance(arr, list) and arr[0].get("submit_id"):
        print(arr[0]["submit_id"])
        break
PY
}

# 确保 audit 用本地样例图存在
resolve_sample_image() {
  local img="${DREAMINA_AUDIT_SAMPLE_IMAGE:-$ROOT/scripts/fixtures/audit-sample.png}"
  if [[ -f "$img" ]]; then
    echo "$img"
    return 0
  fi
  return 1
}

resolve_sample_image_b() {
  local img="${DREAMINA_AUDIT_SAMPLE_IMAGE_B:-$ROOT/scripts/fixtures/audit-sample-b.png}"
  if [[ -f "$img" ]]; then
    echo "$img"
    return 0
  fi
  resolve_sample_image
}

echo "Using: $DREAMINA (HOME=$HOME)"

# --- Help collection (all commands, incl. auth help — no exec) ---
run_cmd version "$DREAMINA" version
run_cmd user_credit "$DREAMINA" user_credit
run_cmd help_root "$DREAMINA" help
run_cmd help_h "$DREAMINA" -h

builtins=(help list_task login logout query_result relogin session user_credit version)
for sub in "${builtins[@]}"; do
  run_cmd "help_${sub}" "$DREAMINA" help "$sub"
  run_cmd "${sub}_h" "$DREAMINA" "$sub" -h
done

for sub in create list ls search find rename update delete rm; do
  run_cmd "session_${sub}_h" "$DREAMINA" session "$sub" -h
done
run_cmd session_bare "$DREAMINA" session

generators=(text2image image2image image_upscale text2video image2video frames2video multiframe2video multimodal2video)
for sub in "${generators[@]}"; do
  run_cmd "help_${sub}" "$DREAMINA" help "$sub"
  run_cmd "${sub}_h" "$DREAMINA" "$sub" -h
done

run_cmd login_checklogin_h "$DREAMINA" login checklogin -h
run_cmd subcmd_session_help "$DREAMINA" session help
run_cmd subcmd_text2image_help "$DREAMINA" text2image help

run_cmd session_list_n5 "$DREAMINA" session list -n 5
run_cmd list_task_limit3 "$DREAMINA" list_task --limit 3

echo "Audit help complete: $AUDIT_DIR ($(ls -1 "$AUDIT_DIR" | wc -l | tr -d ' ') files)"

LOGGED_IN=0
if "$DREAMINA" user_credit >/dev/null 2>&1; then
  LOGGED_IN=1
  echo "Login probe: user_credit OK"
else
  echo "Login probe: user_credit FAILED (keyring / not logged in — Phase B/C may skip)"
fi

SUBMIT_ID=""
NEW_SUBMIT_ID=""
TEXT2IMAGE_SUBMIT_ID=""
TEXT2VIDEO_SUBMIT_ID=""
SAMPLE_IMAGE=""
SAMPLE_IMAGE_B=""

# =============================================================================
# PHASE_QUERY — 只读查询基线
# =============================================================================
echo "--- PHASE_QUERY ---"
exec_capture_verbose version "$DREAMINA" version
exec_capture_verbose help_root "$DREAMINA" help
exec_capture_verbose user_credit "$DREAMINA" user_credit
exec_capture_verbose list_task_limit5 "$DREAMINA" list_task --limit 5
exec_capture_verbose list_task_offset "$DREAMINA" list_task --limit 3 --offset 0
exec_capture_verbose list_task_gen_status "$DREAMINA" list_task --limit 3 --gen_status success

exec_capture_verbose session_bare "$DREAMINA" session
exec_capture_verbose session_list_n5 "$DREAMINA" session list -n 5
exec_capture_verbose session_search_default "$DREAMINA" session search "default"

exec_capture_verbose query_result_noarg "$DREAMINA" query_result
exec_capture_verbose query_result_missing "$DREAMINA" query_result --submit_id=00000000-0000-0000-0000-000000000000

SUBMIT_ID="$(extract_first_submit_id || true)"
if [[ -n "$SUBMIT_ID" ]]; then
  exec_capture_verbose query_result_real "$DREAMINA" query_result --submit_id="$SUBMIT_ID"
else
  echo "Skip exec_query_result_real (no submit_id; login + list_task or set DREAMINA_AUDIT_SUBMIT_ID)"
fi

# =============================================================================
# PHASE_GENERATE_REAL — 真实生成参数（submit --poll=0；text2image 可选 poll 全流程）
# =============================================================================
echo "--- PHASE_GENERATE_REAL ---"

AUDIT_PROMPT_TEXT2IMAGE='一只橘猫坐在窗台上，小清新插画'
AUDIT_PROMPT_TEXT2VIDEO='一只橘猫在窗台上伸懒腰，小清新风格'
AUDIT_PROMPT_IMAGE2IMAGE='转为水彩插画风格'
AUDIT_PROMPT_IMAGE2VIDEO='镜头缓慢推近，微风拂动窗帘'
AUDIT_PROMPT_FRAMES2VIDEO='季节从夏到秋的变化'
AUDIT_PROMPT_MULTIFRAME='角色从左侧转向右侧'
AUDIT_PROMPT_MULTIMODAL='将画面转为电影感镜头'

if [[ "$LOGGED_IN" -eq 1 ]]; then
  echo "text2image submit-only (--poll=0)"
  exec_capture_verbose text2image_submit.json \
    "$DREAMINA" text2image \
    --prompt="$AUDIT_PROMPT_TEXT2IMAGE" \
    --ratio=1:1 \
    --resolution_type=2k \
    --session=0 \
    --poll=0
  TEXT2IMAGE_SUBMIT_ID="$(extract_submit_id_from_exec text2image_submit.json || true)"
  NEW_SUBMIT_ID="${TEXT2IMAGE_SUBMIT_ID:-}"

  if [[ "$POLL_ENABLED" == "1" ]]; then
    echo "text2image with poll (--poll=${POLL_MAX_SEC}, 1s interval per CLI -h)"
    exec_capture_verbose text2image_poll \
      "$DREAMINA" text2image \
      --prompt="$AUDIT_PROMPT_TEXT2IMAGE" \
      --ratio=1:1 \
      --resolution_type=2k \
      --session=0 \
      --poll="$POLL_MAX_SEC"
  else
    echo "Skip exec_text2image_poll (DREAMINA_AUDIT_POLL=0)"
  fi

  echo "text2video submit-only"
  exec_capture_verbose text2video_submit.json \
    "$DREAMINA" text2video \
    --prompt="$AUDIT_PROMPT_TEXT2VIDEO" \
    --duration=5 \
    --ratio=1:1 \
    --session=0 \
    --poll=0
  TEXT2VIDEO_SUBMIT_ID="$(extract_submit_id_from_exec text2video_submit.json || true)"

  if SAMPLE_IMAGE="$(resolve_sample_image)"; then
    SAMPLE_IMAGE_B="$(resolve_sample_image_b)"

    echo "image2image submit-only (sample=$SAMPLE_IMAGE)"
    exec_capture_verbose image2image_submit.json \
      "$DREAMINA" image2image \
      --images="$SAMPLE_IMAGE" \
      --prompt="$AUDIT_PROMPT_IMAGE2IMAGE" \
      --ratio=1:1 \
      --resolution_type=2k \
      --session=0 \
      --poll=0

    echo "image_upscale submit-only (2k, sample=$SAMPLE_IMAGE)"
    exec_capture_verbose image_upscale_submit.json \
      "$DREAMINA" image_upscale \
      --image="$SAMPLE_IMAGE" \
      --resolution_type=2k \
      --session=0 \
      --poll=0

    echo "image2video submit-only"
    exec_capture_verbose image2video_submit.json \
      "$DREAMINA" image2video \
      --image="$SAMPLE_IMAGE" \
      --prompt="$AUDIT_PROMPT_IMAGE2VIDEO" \
      --session=0 \
      --poll=0

    echo "frames2video submit-only"
    exec_capture_verbose frames2video_submit.json \
      "$DREAMINA" frames2video \
      --first="$SAMPLE_IMAGE" \
      --last="$SAMPLE_IMAGE_B" \
      --prompt="$AUDIT_PROMPT_FRAMES2VIDEO" \
      --duration=5 \
      --session=0 \
      --poll=0

    echo "multiframe2video submit-only (2 images)"
    exec_capture_verbose multiframe2video_submit.json \
      "$DREAMINA" multiframe2video \
      --images="$SAMPLE_IMAGE,$SAMPLE_IMAGE_B" \
      --prompt="$AUDIT_PROMPT_MULTIFRAME" \
      --duration=3 \
      --session=0 \
      --poll=0

    echo "multimodal2video submit-only"
    exec_capture_verbose multimodal2video_submit.json \
      "$DREAMINA" multimodal2video \
      --image="$SAMPLE_IMAGE" \
      --prompt="$AUDIT_PROMPT_MULTIMODAL" \
      --duration=5 \
      --ratio=1:1 \
      --session=0 \
      --poll=0
  else
    for skip in image2image_submit.json image_upscale_submit.json image2video_submit.json \
      frames2video_submit.json multiframe2video_submit.json multimodal2video_submit.json; do
      write_skip_note "$skip" "no local sample image (need scripts/fixtures/audit-sample.png or DREAMINA_AUDIT_SAMPLE_IMAGE)"
    done
  fi
else
  echo "Skip PHASE_GENERATE_REAL submits (user_credit gate failed; run scripts/dreamina-cli-audit-interactive.sh locally)"
  for skip in text2image_submit.json text2image_poll text2video_submit.json \
    image2image_submit.json image_upscale_submit.json image2video_submit.json \
    frames2video_submit.json multiframe2video_submit.json multimodal2video_submit.json; do
    write_skip_note "$skip" "not logged in (user_credit failed)"
  done
fi

# =============================================================================
# PHASE_GENERATE_NEGATIVE — 缺参 / bare（辅助对照，非主流程）
# =============================================================================
echo "--- PHASE_GENERATE_NEGATIVE ---"
for sub in "${generators[@]}"; do
  exec_capture_verbose "${sub}_bare" "$DREAMINA" "$sub"
done
if [[ "$LOGGED_IN" -eq 1 ]]; then
  for sub in image2image text2video image2video image_upscale frames2video multiframe2video multimodal2video; do
    exec_capture_verbose "${sub}_missing_args" "$DREAMINA" "$sub" --poll=0
  done
fi

# =============================================================================
# PHASE_POST_QUERY — 生成后 query_result + list_task
# =============================================================================
echo "--- PHASE_POST_QUERY ---"
POST_SUBMIT_ID="${NEW_SUBMIT_ID:-$SUBMIT_ID}"

if [[ -n "$TEXT2IMAGE_SUBMIT_ID" ]]; then
  exec_capture_verbose query_result_text2image_submit \
    "$DREAMINA" query_result --submit_id="$TEXT2IMAGE_SUBMIT_ID"
fi
if [[ -n "$TEXT2VIDEO_SUBMIT_ID" ]]; then
  exec_capture_verbose query_result_text2video_submit \
    "$DREAMINA" query_result --submit_id="$TEXT2VIDEO_SUBMIT_ID"
fi
if [[ -n "$POST_SUBMIT_ID" ]]; then
  exec_capture_verbose query_result_post "$DREAMINA" query_result --submit_id="$POST_SUBMIT_ID"
else
  echo "Skip exec_query_result_post (no submit_id from Phase B or list_task)"
fi
exec_capture_verbose list_task_post "$DREAMINA" list_task --limit 5

{
  echo "audit_time=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "dreamina=$DREAMINA"
  echo "home=$HOME"
  if [[ "$LOGGED_IN" -eq 1 ]]; then
    echo "user_credit_probe=logged_in"
  else
    echo "user_credit_probe=not_logged_in"
  fi
  echo "poll_enabled=$POLL_ENABLED"
  echo "poll_max_sec=$POLL_MAX_SEC"
  echo "prompt_text2image=$AUDIT_PROMPT_TEXT2IMAGE"
  echo "note=audit skips logout/relogin/login; Cursor/agent shells may lack macOS keyring (secret not found in keyring)"
  if [[ -n "${SUBMIT_ID:-}" ]]; then
    echo "audit_submit_id=$SUBMIT_ID"
  fi
  if [[ -n "${TEXT2IMAGE_SUBMIT_ID:-}" ]]; then
    echo "audit_text2image_submit_id=$TEXT2IMAGE_SUBMIT_ID"
  fi
  if [[ -n "${TEXT2VIDEO_SUBMIT_ID:-}" ]]; then
    echo "audit_text2video_submit_id=$TEXT2VIDEO_SUBMIT_ID"
  fi
  if [[ -n "${NEW_SUBMIT_ID:-}" ]]; then
    echo "audit_new_submit_id=$NEW_SUBMIT_ID"
  fi
  if [[ -n "${POST_SUBMIT_ID:-}" ]]; then
    echo "audit_post_query_submit_id=$POST_SUBMIT_ID"
  fi
  if [[ -n "${SAMPLE_IMAGE:-}" ]]; then
    echo "audit_sample_image=$SAMPLE_IMAGE"
  fi
} >"$AUDIT_DIR/LOGIN_STATUS.txt"

echo "Wrote LOGIN_STATUS.txt"
echo "Audit complete. See docs/CLI_EXEC_CATALOG.md for command classification."
