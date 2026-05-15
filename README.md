# dreamina-java-sdk

纯 Java SDK（无 Spring 依赖），用于通过本地 `dreamina` CLI 调用即梦能力。适合在普通 Java 应用、命令行工具、批处理任务或其它框架中直接集成。

Spring Boot 应用请使用 [dreamina-spring-boot-starter](../dreamina-spring-boot-starter)。

## 功能概览

- 基于 Apache Commons Exec 执行本地 `dreamina` 命令
- 统一封装超时、非零退出码、可执行文件不可用等异常
- 支持官方 CLI 的内建命令与主要生成命令
- 在通用 `DreaminaCliResult` 基础上，提供结构化结果对象
- 提供本地 smoke 入口，便于在真实机器上快速自测

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

## 命令封装

执行入口为 [`DreaminaCliExecutor`](src/main/java/io/github/hiwepy/dreamina/cli/DreaminaCliExecutor.java)。

### 内建命令

| CLI | 结构化方法 |
|-----|------------|
| `help` | `helpInfo()` / `helpInfo(subcommand)` |
| `version` | `versionInfo()` |
| `user_credit` | `userCreditInfo()` |
| `list_task` | `listTaskInfo()` |
| `query_result` | `queryResultInfo(submitId)` |
| `login --headless` | `loginHeadlessInfo()` |
| `session list` | `sessionListInfo()` |
| `session search` | `sessionSearchInfo(keyword)` |
| `session create` | `sessionCreateInfo(...)` |
| `session rename` | `sessionRenameInfo(...)` |

### 生成命令

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

## 结果模型

SDK 保留两层结果：

1. **原始结果**：[`DreaminaCliResult`](src/main/java/io/github/hiwepy/dreamina/cli/DreaminaCliResult.java)  
   包含 `stdout`、`stderr`、`exitCode`、`success`、基础 best-effort 解析字段。

2. **结构化结果**：[`DreaminaCliTypedResult<T>`](src/main/java/io/github/hiwepy/dreamina/cli/DreaminaCliTypedResult.java)  
   例如：
   - `DreaminaVersionResult`
   - `DreaminaUserCreditResult`
   - `DreaminaTaskListResult`
   - `DreaminaQueryResult`
   - `DreaminaGenerateSubmitResult`
   - `DreaminaSessionListResult`
   - `DreaminaLoginResult`

结构化解析由 [`DreaminaCliStructuredPayloadMapper`](src/main/java/io/github/hiwepy/dreamina/cli/parser/DreaminaCliStructuredPayloadMapper.java) 负责，优先解析 JSON，必要时回退到文本提取。

## 本地自测

SDK 提供真实 CLI 冒烟入口：

```bash
cd dreamina-java-sdk
mvn test-compile exec:java \
  -Dexec.mainClass=io.github.hiwepy.dreamina.cli.DreaminaCliLocalSmokeMain \
  -Dexec.classpathScope=test
```

如只想做低风险检查，可通过环境变量关闭生成任务：

```bash
DREAMINA_SMOKE_SKIP_GENERATE=true mvn test-compile exec:java \
  -Dexec.mainClass=io.github.hiwepy.dreamina.cli.DreaminaCliLocalSmokeMain \
  -Dexec.classpathScope=test
```

## 发布说明

本模块已补齐与 `openclaw-java-sdk` 同风格的发布元数据：

- `url`
- `licenses`
- `scm`
- `developers`
- `distributionManagement`
- `release` profile

本地发布：

```bash
mvn clean install -DskipTests
```

正式发布时可使用：

```bash
mvn -Prelease clean deploy
```

前提是本机已配置 GPG 与 Central 发布凭据。
