# dreamina-java-sdk

纯 Java SDK（无 Spring 依赖），用于通过本地 `dreamina` CLI 调用即梦能力。适合在普通 Java 应用、命令行工具、批处理任务或其它框架中直接集成。

Spring Boot 应用请使用 [dreamina-spring-boot-starter](../dreamina-spring-boot-starter)。

官方 CLI 体验指南：[飞书 Wiki](https://bytedance.larkoffice.com/wiki/FVTwwm0bGiishxkKOoScdHR2nsg)（编排 SOP 与 FAQ 参考；**命令与 flag 以本机 `dreamina help` 为准**）。

## 功能概览

- 基于 Apache Commons Exec 执行本地 `dreamina` 命令
- 统一封装超时、非零退出码、可执行文件不可用等异常
- 支持官方 CLI 的内建命令与全部生成命令
- 在通用 `DreaminaCliResult` 基础上，提供结构化结果对象
- 提供本地 smoke 入口，便于在真实机器上快速自测

## 安装 CLI

```bash
curl -fsSL https://jimeng.jianying.com/cli | bash
dreamina version
dreamina help
```

## Maven 依赖

```xml
<dependency>
  <groupId>io.github.hiwepy</groupId>
  <artifactId>dreamina-java-sdk</artifactId>
  <version>1.0.x.20260515-SNAPSHOT</version>
</dependency>
```

## 快速开始

```java
import io.github.hiwepy.dreamina.cli.DreaminaCliExecutor;
import io.github.hiwepy.dreamina.DreaminaCliProperties;
import io.github.hiwepy.dreamina.cli.DreaminaCliTypedResult;
import io.github.hiwepy.dreamina.cli.DreaminaGenerateSubmitResult;

DreaminaCliProperties properties = new DreaminaCliProperties();
properties.setExecutable("dreamina");
properties.setCommandTimeoutMillis(120_000L);

DreaminaCliExecutor executor = new DreaminaCliExecutor(properties);

DreaminaCliTypedResult<DreaminaGenerateSubmitResult> submit =
        executor.text2ImageSubmit("a cat portrait", java.util.List.of("--ratio=1:1", "--poll=0"));

String submitId = submit.getStructured().getSubmitId();
```

## 配置对象

核心配置类为 [`DreaminaCliProperties`](src/main/java/io/github/hiwepy/dreamina/DreaminaCliProperties.java)：

| 属性 | 说明 |
|------|------|
| `executable` | CLI 可执行文件名或绝对路径，默认 `dreamina` |
| `workingDirectory` | 执行工作目录，可选 |
| `commandTimeoutMillis` | 单次命令执行超时 |
| `defaultPollIntervalSeconds` | 业务层轮询建议间隔 |

## Agent 编排 SOP

推荐流程（与官方 Wiki 一致）：

```
1. CHECK   → user_creditInfo()           # 确认登录与额度
2. SUBMIT  → *Submit(..., poll=0)      # 异步提交，拿 submit_id
3. POLL    → queryResultInfo(submitId)   # 周期查询 gen_status
4. OPTIONAL→ listTaskInfo(gen_status=success)
```

**`--poll` 语义**：提交命令带 `--poll=N` 时，CLI 每秒轮询最多 N 秒；完成则直出结果，超时则返回 `querying`，后续用 `query_result` 继续查。

## 登录与账号（OAuth Device Flow）

当前 CLI 使用 OAuth Device Flow（**已无 `login --debug`**）：

| CLI | SDK 方法 |
|-----|----------|
| `dreamina login` | `login()` |
| `dreamina login --headless` | `loginHeadless()` / `loginHeadlessInfo()` |
| `dreamina login checklogin --device_code=... --poll=30` | `checkLogin(deviceCode, pollSeconds, ...)` |
| `dreamina logout` | `logout()` |
| `dreamina relogin` | `relogin()` |
| `dreamina relogin --headless` | `relogin(List.of("--headless"))` |
| `dreamina user_credit` | `userCreditInfo()` |
| `dreamina version` | `versionInfo()` |

Headless 流程：`loginHeadlessInfo()` 解析 `device_code` → `checkLogin(...)` 轮询完成授权。

## 命令总表

执行入口：[`DreaminaCliExecutor`](src/main/java/io/github/hiwepy/dreamina/cli/DreaminaCliExecutor.java)。

### Built-in Commands

| CLI | 结构化方法 | 原始方法 |
|-----|------------|----------|
| `help` | `helpInfo()` / `helpInfo(subcommand)` | `help()` |
| `version` | `versionInfo()` | `version()` |
| `user_credit` | `userCreditInfo()` | `userCredit()` |
| `login` / `logout` / `relogin` | `loginHeadlessInfo()` 等 | `login()` / `logout()` / `relogin()` |
| `session create/list/search/rename/delete` | `sessionCreateInfo()` 等 | `sessionCreate()` 等 |
| `list_task` | `listTaskInfo()` / `listTaskInfo(request)` | `listTask()` |
| `query_result` | `queryResultInfo()` / `queryResultInfo(request)` | `queryResult()` |

### Generator Commands

| CLI | 结构化方法 |
|-----|------------|
| `text2image` | `text2ImageSubmit(...)` |
| `image2image` | `image2ImageSubmit(...)` |
| `image_upscale` | `imageUpscaleSubmit(...)` |
| `text2video` | `text2VideoSubmit(...)` |
| `image2video` | `image2VideoSubmit(...)` |
| `frames2video` | `frames2VideoSubmit(...)` |
| `multiframe2video` | `multiframe2VideoSubmit(...)` |
| `multimodal2video` | `multimodal2VideoSubmit(...)` |

通用扩展：`invoke(subcommand, additionalRawArgs)` 或各 Request 的 `additionalRawArgs`。

## Session 工作区

| CLI | SDK |
|-----|-----|
| `dreamina session create [name]` | `sessionCreateInfo(...)` |
| `dreamina session list [-n N]` | `sessionListInfo()` / `sessionListInfo(List.of("-n=100"))` |
| `dreamina session search "keyword"` | `sessionSearchInfo(keyword)` |
| `dreamina session rename <id> <name>` | `sessionRenameInfo(id, name)` |
| `dreamina session delete <id>` | `sessionDelete(id)` |

所有生成命令支持 `--session=<id>`（默认 0 为默认对话；Session 0 不可 rename/delete）。

## 任务查询

```java
// 查询并下载
DreaminaQueryResultRequest query = DreaminaQueryResultRequest.builder()
    .submitId(submitId)
    .downloadDir("./downloads")
    .build();
executor.queryResultInfo(query);

// 列表筛选
DreaminaListTaskRequest list = DreaminaListTaskRequest.builder()
    .genStatus("success")
    .genTaskType("text2image")
    .limit(20)
    .offset(0)
    .build();
executor.listTaskInfo(list);
```

## 生成命令 flag 速查

> 完整说明请运行 `dreamina help <subcommand>`。

| 命令 | 关键 flag |
|------|-----------|
| `text2image` | `--prompt`, `--ratio`, `--resolution_type`, `--model_version`, `--session`, `--poll` |
| `text2video` | `--prompt`, `--duration`, `--ratio`, `--video_resolution`, `--model_version`（仅 seedance 四型号）, `--session`, `--poll` |
| `image2image` | `--images`, `--prompt`, `--ratio`, `--resolution_type`（2k/4k）, `--model_version`（4.0+）, `--session`, `--poll` |
| `image_upscale` | `--image`, `--resolution_type`（2k/4k/8k）, `--session`, `--poll` |
| `image2video` | `--image`, `--prompt`, `--duration`, `--model_version`, `--video_resolution`, `--session`, `--poll` |
| `frames2video` | `--first`, `--last`, `--prompt`, `--duration`, `--model_version`, `--video_resolution`, `--session`, `--poll` |
| `multiframe2video` | `--images`, 2 图：`--prompt`+`--duration`；3+ 图：重复 `--transition-prompt` / `--transition-duration` |
| `multimodal2video` | 重复 `--image`/`--video`/`--audio`, `--prompt`, `--duration`, `--ratio`, `--model_version`, `--video_resolution`, `--session`, `--poll` |

**视频分辨率**：CLI 使用小写 `720p` / `1080p`（`seedance2.0_vip` 可选 1080p）。

## 结果模型

1. **原始结果**：[`DreaminaCliResult`](src/main/java/io/github/hiwepy/dreamina/cli/DreaminaCliResult.java)
2. **结构化结果**：[`DreaminaCliTypedResult<T>`](src/main/java/io/github/hiwepy/dreamina/cli/DreaminaCliTypedResult.java)

常见类型：`DreaminaVersionResult`、`DreaminaUserCreditResult`、`DreaminaTaskListResult`、`DreaminaQueryResult`、`DreaminaGenerateSubmitResult`、`DreaminaSessionListResult`、`DreaminaLoginResult`。

## FAQ 与本地文件

| 路径 | 说明 |
|------|------|
| `~/.dreamina_cli/config.toml` | 环境配置 |
| `~/.dreamina_cli/tasks.db` | 本地任务记录 |
| `~/.dreamina_cli/logs/` | 运行日志 |

排障：先 `user_credit` 确认登录；生成失败时提供完整命令、报错与 logs 目录内容。

## 本地自测

```bash
cd dreamina-java-sdk
mvn test-compile exec:java \
  -Dexec.mainClass=io.github.hiwepy.dreamina.cli.DreaminaCliLocalSmokeMain \
  -Dexec.classpathScope=test
```

跳过生成任务（省积分）：

```bash
DREAMINA_SMOKE_SKIP_GENERATE=true mvn test-compile exec:java \
  -Dexec.mainClass=io.github.hiwepy.dreamina.cli.DreaminaCliLocalSmokeMain \
  -Dexec.classpathScope=test
```

## 单元测试与覆盖率

```bash
cd dreamina-java-sdk
mvn test                              # bash mock CLI，不依赖真实 dreamina 二进制
mvn test jacoco:report                # 报告：target/site/jacoco/index.html
mvn clean verify                      # DreaminaCliExecutor LINE+BRANCH 100% 门禁（jacoco:check）
```

## 发布说明

```bash
mvn clean install -DskipTests
mvn -Prelease clean deploy   # 需 GPG 与 Central 凭据
```
