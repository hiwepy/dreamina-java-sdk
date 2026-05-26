# Dreamina CLI 执行目录（CLI Exec Catalog）

> 采集脚本：`scripts/dreamina-cli-audit.sh`（三阶段） / `scripts/dreamina-cli-audit-interactive.sh`（本机 Terminal，需 Keychain）  
> 原始输出：`.cli-audit/exec_*.txt`、`.cli-audit/*_h.txt`  
> SDK 映射：[`DreaminaCliStructuredPayloadMapper`](../src/main/java/io/github/hiwepy/dreamina/cli/parser/DreaminaCliStructuredPayloadMapper.java)

**约束**：审计与 SDK 测试**不执行** `login` / `logout` / `relogin`，以免打断 OAuth。Cursor Agent 沙箱常因 macOS Keychain 导致 `user_credit` exit 1；已登录样例来自历史 `.cli-audit/list_task_n3.txt` 等本机采集。

---

## 命令分类总览

| 分类 | 命令 | 是否 exec | body 类型 |
|------|------|-----------|-----------|
| **Query** | `version`, `help`, `-h`, `user_credit`, `list_task`, `query_result`, `session`（无子命令）, `session list`/`ls`, `session search`/`find` | 是（Query 阶段） | 见下表 |
| **Generate** | `text2image`, `image2image`, `image_upscale`, `text2video`, `image2video`, `frames2video`, `multiframe2video`, `multimodal2video` | 是（PHASE_GENERATE_REAL，有效参数 + `--poll=0`） | `DreaminaGenerateSubmit` |
| **Session mutate** | `session create`, `rename`, `update`, `delete`/`rm` | **跳过**（仅 `-h`） | `DreaminaSessionMutation` / `DreaminaSessionDelete` |
| **Auth** | `login`, `logout`, `relogin`, `login checklogin` | **仅 `-h`**，不 exec | `DreaminaLogin` / `DreaminaLogout` / `DreaminaRelogin` / `DreaminaCheckLogin` |

---

## Phase A — Query 基线（PHASE_QUERY）

执行顺序：`user_credit` → `list_task --limit 5` → `session list -n 5` → `session search` → `query_result`（历史 submit_id）

### `version`

| 项 | 值 |
|----|-----|
| 分类 | Query |
| argv | `dreamina version` |
| env | `HOME`, `PATH`（含 `~/.local/bin`） |
| exit | **0** |
| 输出 | JSON：`version`, `commit`, `build_time` |
| 样例 | `.cli-audit/exec_version.txt` |
| body | `DreaminaCliResponse<DreaminaVersion>` |
| mapper | ✅ `mapVersion` |
| 缺口 | 无 |

### `help` / `-h`

| 项 | 值 |
|----|-----|
| 分类 | Query |
| argv | `dreamina help` / `dreamina -h` |
| exit | **0** |
| 输出 | 英文 Usage + Built-in / Generator 列表 |
| 样例 | `.cli-audit/help_root.txt`, `.cli-audit/exec_help_root.txt` |
| body | `DreaminaCliResponse<DreaminaHelp>`（全文 `getCombinedText()`） |
| mapper | ✅ `mapHelp` |
| 缺口 | 无 |

### `user_credit`

| 项 | 值 |
|----|-----|
| 分类 | Query |
| argv | `dreamina user_credit` |
| exit（已登录） | **0**，JSON |
| exit（未登录/Agent） | **1**，stdout/stderr **空** |
| 样例（已登录 JSON） | 测试内嵌 + 历史 `user_credit.txt`（exit 0 时应有 JSON） |
| 样例（Agent 失败） | `.cli-audit/exec_user_credit.txt`（exit 1，空） |
| body | `DreaminaCliResponse<DreaminaUserCredit>` |
| mapper | ✅ `mapUserCredit`；未登录时 `body=null` |
| 缺口 | 无；调用方应检查 `isSuccess()` |

**典型 JSON（已登录）**：

```json
{
  "total_credit": 4388,
  "user_id": 1552973852847448,
  "user_name": "",
  "vip_level": "maestro"
}
```

### `list_task`

| 项 | 值 |
|----|-----|
| 分类 | Query |
| argv | `dreamina list_task --limit 5`；`--offset 0`；`--gen_status success` |
| exit（已登录） | **0** |
| exit（Agent 未登录） | **1**，空 |
| 样例（真实 JSON） | `.cli-audit/list_task_n3.txt`, `.cli-audit/list_task_limit3.txt` |
| exec | `.cli-audit/exec_list_task_limit3.txt`（Agent exit 1） |
| body | `DreaminaCliResponse<List<DreaminaTaskItem>>` |
| mapper | ✅ `mapTaskList`；含 `commerce_info`, `result_json.images[]`（可仅 width/height） |
| 缺口 | 无 |

**首条 submit_id（catalog 用）**：`c3c0774f-6715-4c0a-b044-f961acf38314`（来自 `list_task_n3.txt`）

### `query_result`

| 项 | 值 |
|----|-----|
| 分类 | Query |
| argv | 无参；`--submit_id=00000000-...`（无效）；`--submit_id=<真实>` |
| exit | 无效 id / 未登录：**0 或 1**，常无 JSON |
| 样例（成功 JSON） | 测试 `mapQueryResult_shouldMapProductionLikePayload` |
| exec | `.cli-audit/exec_query_result_real.txt`（Agent exit 1）；`.cli-audit/query_result_live.txt` |
| body | `DreaminaCliResponse<DreaminaQueryResult>` |
| mapper | ✅ `mapQueryResult` + `queue_info.debug_info` → `parsedDebugInfo` |
| 缺口 | 空 stdout 时 `body=null`，属预期 |

### `session`（无子命令）

| 项 | 值 |
|----|-----|
| 分类 | Query |
| argv | `dreamina session` |
| exit | **0** |
| 输出 | Usage（等价 `session -h`） |
| exec | `.cli-audit/exec_session_bare.txt` |
| body | 原始文本；SDK 用 `sessionInfo()` 返回 help 类组合 |
| 缺口 | 无 |

### `session list` / `ls`

| 项 | 值 |
|----|-----|
| 分类 | Query |
| argv | `dreamina session list -n 5` |
| exit（已登录） | **0** |
| 输出 | 固定列宽表格 |
| 样例 | 测试 `mapSessionList_shouldParseLocalCliTable` |
| exec | `.cli-audit/exec_session_list_n5.txt`（Agent exit 1） |
| body | `DreaminaCliResponse<DreaminaSessionList>` |
| mapper | ✅ `mapSessionList`（正则四列：ID/NAME/PINNED/UPDATED_AT） |
| 缺口 | 无 |

### `session search` / `find`

| 项 | 值 |
|----|-----|
| 分类 | Query |
| argv | `dreamina session search "default"` |
| exit（已登录） | **0** |
| 输出 | `Found N sessions...` + 三列表格 |
| exec | `.cli-audit/exec_session_search_default.txt` |
| body | `DreaminaCliResponse<DreaminaSessionSearch>` |
| mapper | ✅ `mapSessionSearch` |
| 缺口 | 无 |

---

## 真实生成流程（PHASE_GENERATE_REAL）

**策略（已登录）**：对每种生成命令用 **有效必填参数** + `--poll=0` 做 submit-only 采集；`text2image` 额外可选 `--poll=N` 采集轮询进度（默认 `N=120`，CLI 按 1s 间隔调用 `query_result`）。所有 exec 经 `exec_capture_verbose` 写入，**stdout/stderr 完整保留**（含字节数与时间戳）。

**样例图**：`scripts/fixtures/audit-sample.png`（512×512）；可通过 `DREAMINA_AUDIT_SAMPLE_IMAGE` 覆盖。无样例图时 image 类命令写入 `exec_*_submit.json.txt` 的 SKIPPED 说明。

**环境变量**：

| 变量 | 默认 | 说明 |
|------|------|------|
| `DREAMINA_AUDIT_POLL` | `1` | 是否采集 `exec_text2image_poll.txt` |
| `DREAMINA_AUDIT_POLL_MAX_SEC` | `120` | `text2image --poll=N` 最长等待秒数 |
| `DREAMINA_AUDIT_SAMPLE_IMAGE` | `scripts/fixtures/audit-sample.png` | image 类命令输入 |

### 通用提交 JSON 形态（submit-only，`--poll=0`）

| 字段 | 说明 |
|------|------|
| `submit_id` | UUID |
| `logid` | 可选 |
| `gen_status` | 常为 `querying` |
| `credit_count` | 扣费积分 |

**stdout 形态**：单行或多行 **JSON 对象**（非数组）。exit **0** 表示提交成功。

**poll 模式 stdout**（`--poll>0`）：CLI 在 stderr/stdout 输出轮询进度行（如 queue 状态），最终仍可能输出含 `result_json` / `queue_info` 的 JSON。详见 `exec_text2image_poll.txt`。

body：`DreaminaCliResponse<DreaminaGenerateSubmit>` — ✅ `mapGenerateSubmit`

### 命令 argv 与 exec 文件

| 命令 | 真实 argv（已登录，submit-only） | exec 文件 |
|------|----------------------------------|-----------|
| `text2image` | `--prompt="一只橘猫坐在窗台上，小清新插画" --ratio=1:1 --resolution_type=2k --session=0 --poll=0` | `exec_text2image_submit.json.txt` |
| `text2image`（poll） | 同上，`--poll=120`（或 `DREAMINA_AUDIT_POLL_MAX_SEC`） | `exec_text2image_poll.txt` |
| `text2video` | `--prompt="一只橘猫在窗台上伸懒腰，小清新风格" --duration=5 --ratio=1:1 --session=0 --poll=0` | `exec_text2video_submit.json.txt` |
| `image2image` | `--images=<sample> --prompt="转为水彩插画风格" --ratio=1:1 --resolution_type=2k --session=0 --poll=0` | `exec_image2image_submit.json.txt` |
| `image_upscale` | `--image=<sample> --resolution_type=2k --session=0 --poll=0` | `exec_image_upscale_submit.json.txt` |
| `image2video` | `--image=<sample> --prompt="镜头缓慢推近，微风拂动窗帘" --session=0 --poll=0` | `exec_image2video_submit.json.txt` |
| `frames2video` | `--first=<sample> --last=<sample-b> --prompt="季节从夏到秋的变化" --duration=5 --session=0 --poll=0` | `exec_frames2video_submit.json.txt` |
| `multiframe2video` | `--images=<a>,<b> --prompt="角色从左侧转向右侧" --duration=3 --session=0 --poll=0` | `exec_multiframe2video_submit.json.txt` |
| `multimodal2video` | `--image=<sample> --prompt="将画面转为电影感镜头" --duration=5 --ratio=1:1 --session=0 --poll=0` | `exec_multimodal2video_submit.json.txt` |

**必填 flags（来自 `*_h.txt`）**：

| 命令 | 必填 | `--poll` 默认行为 |
|------|------|-------------------|
| `text2image` | `--prompt` | `0`=仅提交；`N`=最多 N 秒、1s 间隔轮询 |
| `text2video` | `--prompt` | 同上 |
| `image2image` | `--images`, `--prompt` | 同上 |
| `image_upscale` | `--image`, `--resolution_type` | 同上 |
| `image2video` | `--image`, `--prompt` | 同上 |
| `frames2video` | `--first`, `--last`, `--prompt` | 同上 |
| `multiframe2video` | `--images`（2+）；2 张时可 `--prompt` | 同上 |
| `multimodal2video` | 至少一个 `--image` 或 `--video` | 同上 |

### PHASE_GENERATE_NEGATIVE（辅助对照）

| 类型 | 说明 | 文件 |
|------|------|------|
| bare | 无 flags | `exec_<cmd>_bare.txt` |
| missing_args | 仅 `--poll=0` | `exec_<cmd>_missing_args.txt` |

---

## Phase C — Post-generate Query（PHASE_POST_QUERY）

| 命令 | argv | 说明 |
|------|------|------|
| `query_result` | `--submit_id=<text2image submit id>` | `.cli-audit/exec_query_result_text2image_submit.txt` |
| `query_result` | `--submit_id=<text2video submit id>` | `.cli-audit/exec_query_result_text2video_submit.txt` |
| `query_result` | `--submit_id=<Phase B 新 id 或 list_task 首条>` | `.cli-audit/exec_query_result_post.txt` |
| `list_task` | `--limit 5` | 验证新任务入列表；`.cli-audit/exec_list_task_post.txt` |

**`query_result` stdout 形态**（进行中任务）：

```json
{
  "submit_id": "...",
  "gen_status": "querying",
  "credit_count": 3,
  "queue_info": {
    "queue_status": "Generating",
    "queue_idx": 0,
    "priority": 1,
    "queue_length": 0,
    "debug_info": "{...}"
  }
}
```

完成时 `gen_status` 可为 `success`，并含 `result_json.images[]` / `videos[]`。

---

## Auth — 仅文档（不 exec）

| 命令 | 帮助文件 | SDK body | 备注 |
|------|----------|----------|------|
| `login` | `login_h.txt` | `DreaminaLogin` | Device Flow / OAuth 复用文本 |
| `login checklogin` | `login_checklogin_h.txt` | `DreaminaCheckLogin` | 成功时可能空 stdout |
| `logout` | `logout_h.txt` | `DreaminaLogout` | **禁止 audit exec** |
| `relogin` | `relogin_h.txt` | `DreaminaRelogin` | **禁止 audit exec** |

---

## Session mutate — 跳过 exec

| 命令 | 帮助 | SDK | audit |
|------|------|-----|-------|
| `session create` | `session_create_h.txt` | `mapSessionMutation` CREATE | 仅 `-h` |
| `session rename` / `update` | `session_rename_h.txt` | RENAME | 仅 `-h` |
| `session delete` / `rm` | `session_delete_h.txt` | `mapSessionDelete` | 仅 `-h` |

---

## 环境与已知问题

| 问题 | 现象 | 处理 |
|------|------|------|
| Agent 无 Keychain | `user_credit` exit 1，空输出 | 运行 `scripts/dreamina-cli-audit-interactive.sh` |
| 未登录 | `list_task` / `session list` exit 1 | 本机 `dreamina login` 后重跑 |
| 无效 submit_id | `query_result` 无 JSON | SDK `body=null`，检查 exit + combinedText |

`LOGIN_STATUS.txt` 记录：`user_credit_probe`, `poll_enabled`, `poll_max_sec`, `prompt_text2image`, `audit_submit_id`, `audit_text2image_submit_id`, `audit_text2video_submit_id`, `audit_new_submit_id`, `audit_post_query_submit_id`, `audit_sample_image`。

---

## SDK 方法对照

| CLI | Executor 结构化方法 | Mapper |
|-----|---------------------|--------|
| `version` | `versionInfo()` | `mapVersion` |
| `user_credit` | `userCreditInfo()` | `mapUserCredit` |
| `list_task` | `listTaskInfo(...)` | `mapTaskList` |
| `query_result` | `queryResultInfo(...)` | `mapQueryResult` |
| `text2image` 等 | `*Submit(...)` | `mapGenerateSubmit` |
| `session list` | `sessionListInfo(...)` | `mapSessionList` |
| `session search` | `sessionSearchInfo(...)` | `mapSessionSearch` |
| `help` | `helpInfo(...)` | `mapHelp` |

---

## 复现采集

```bash
cd dreamina-java-sdk
chmod +x scripts/dreamina-cli-audit.sh scripts/dreamina-cli-audit-interactive.sh

# Agent / CI（可能无 Keychain）
./scripts/dreamina-cli-audit.sh

# 本机已登录 Terminal
./scripts/dreamina-cli-audit-interactive.sh

# 指定历史 submit_id
DREAMINA_AUDIT_SUBMIT_ID=c3c0774f-6715-4c0a-b044-f961acf38314 ./scripts/dreamina-cli-audit.sh
```

更新本目录后，请同步 [`DreaminaCliStructuredPayloadMapperTest`](../src/test/java/io/github/hiwepy/dreamina/cli/parser/DreaminaCliStructuredPayloadMapperTest.java) 中的 JSON 片段。

---

## 查询刷新

在交互式审计（`dreamina-cli-audit-interactive.sh`）提交生成任务后，可用 **`scripts/dreamina-cli-audit-refresh-query.sh`** 或 Agent 直接调用 `dreamina query_result --submit_id=…` 再次拉取状态（**不** login/logout/relogin）。

| submit_id | gen_status（刷新） | 输出文件 |
|-----------|-------------------|----------|
| `262760ae-2694-439f-9258-7a1fb20c33d4` | success | `.cli-audit/exec_query_refresh_262760ae.txt` |
| `fe6e2b86-b620-42ab-99ed-d235b6404b8d` | success | `.cli-audit/exec_query_refresh_fe6e2b86.txt` |
| `ee6fa746-cfb3-4708-8de4-7ef87df9d957` | success | `.cli-audit/exec_query_refresh_ee6fa746.txt` |
| `7045230a-b96f-4470-8212-b424a609782c` | success | `.cli-audit/exec_query_refresh_7045230a.txt` |
| `b15b3096-baa7-466d-b302-0bcc4e5590dd` | querying | `.cli-audit/exec_query_refresh_b15b3096.txt` |
| `1d6f7404-eb4e-45ce-854b-6bd027bae148` | querying | `.cli-audit/exec_query_refresh_1d6f7404.txt` |
| `3c73d687-be9a-4908-8fae-81b2a2e9718f` | querying | `.cli-audit/exec_query_refresh_3c73d687.txt` |
| `beefb889-845f-4dde-bdf7-c85db895d1c9` | querying | `.cli-audit/exec_query_refresh_beefb889.txt` |
| `fa8e95ea-ac2e-41fc-be21-ea78c96371c3` | querying | `.cli-audit/exec_query_refresh_fa8e95ea.txt` |

汇总（2026-05-26 刷新）：共 **9** 个 submit_id；**success=4**，**querying=5**，**fail=0**（刷新时点）。辅助输出：`.cli-audit/exec_list_task_refresh.txt`、`.cli-audit/exec_user_credit_refresh.txt`。

```bash
cd dreamina-java-sdk
./scripts/dreamina-cli-audit-refresh-query.sh
```
