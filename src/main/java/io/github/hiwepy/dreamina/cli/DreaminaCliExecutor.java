package io.github.hiwepy.dreamina.cli;

import io.github.hiwepy.dreamina.util.DreaminaStrings;
import io.github.hiwepy.dreamina.DreaminaCliProperties;
import io.github.hiwepy.dreamina.cli.DreaminaCliSubcommands;
import io.github.hiwepy.dreamina.exception.DreaminaCliExecutableFailureException;
import io.github.hiwepy.dreamina.exception.DreaminaCliException;
import io.github.hiwepy.dreamina.exception.DreaminaCliNonZeroExitException;
import io.github.hiwepy.dreamina.exception.DreaminaCliTimeoutException;
import io.github.hiwepy.dreamina.cli.parser.DreaminaCliStructuredPayloadMapper;
import io.github.hiwepy.dreamina.cli.parser.DreaminaCliOutputParser;
import io.github.hiwepy.dreamina.cli.parser.DreaminaParsedFields;
import io.github.hiwepy.dreamina.cli.opts.DreaminaFrames2VideoRequest;
import io.github.hiwepy.dreamina.cli.opts.DreaminaImage2ImageRequest;
import io.github.hiwepy.dreamina.cli.opts.DreaminaImage2VideoRequest;
import io.github.hiwepy.dreamina.cli.opts.DreaminaImageUpscaleRequest;
import io.github.hiwepy.dreamina.cli.opts.DreaminaListTaskRequest;
import io.github.hiwepy.dreamina.cli.opts.DreaminaQueryResultRequest;
import io.github.hiwepy.dreamina.cli.opts.DreaminaMultiframe2VideoRequest;
import io.github.hiwepy.dreamina.cli.opts.DreaminaMultimodal2VideoRequest;
import io.github.hiwepy.dreamina.cli.opts.DreaminaText2ImageRequest;
import io.github.hiwepy.dreamina.cli.opts.DreaminaText2VideoRequest;
import io.github.hiwepy.dreamina.cli.DreaminaCliResult;
import io.github.hiwepy.dreamina.cli.DreaminaCliTypedResult;
import io.github.hiwepy.dreamina.cli.DreaminaDeviceLoginResult;
import io.github.hiwepy.dreamina.cli.DreaminaGenerateSubmitResult;
import io.github.hiwepy.dreamina.cli.DreaminaHelpResult;
import io.github.hiwepy.dreamina.cli.DreaminaLoginResult;
import io.github.hiwepy.dreamina.cli.DreaminaQueryResult;
import io.github.hiwepy.dreamina.cli.DreaminaSessionListResult;
import io.github.hiwepy.dreamina.cli.DreaminaSessionMutationResult;
import io.github.hiwepy.dreamina.cli.DreaminaSessionSearchResult;
import io.github.hiwepy.dreamina.cli.DreaminaTaskListResult;
import io.github.hiwepy.dreamina.cli.DreaminaUserCreditResult;
import io.github.hiwepy.dreamina.cli.DreaminaVersionResult;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecuteResultHandler;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;

/**
 * 基于 Apache Commons Exec 的 Dreamina CLI 进程执行封装。
 * <p>
 * 设计要点（对齐 OpenClaw Jimeng CLI 技能的「能力边界 + 编排节奏」启示，但不承担业务编排）：
 * </p>
 * <ul>
 *   <li><b>能力分组</b>：内置 {@code help}、账号/会话（含 {@code login checklogin}、{@code session *}）、图片生成、视频生成、任务查询；
 *       与 {@link DreaminaCliSubcommands} 常量一致，上层可按 「CHECK(user_credit) → SUBMIT(gen) → POLL(query_result)」 拼装流程。</li>
 *   <li><b>扩展位</b>：{@link #invoke(String, List)} 与各 {@code xxx(List&lt;String&gt; additionalRawArgs)}
 *       过载允许挂载官方新增 flag，无需为每个参数加长方法签名。</li>
 *   <li><b>执行语义</b>：统一超时、流捕获与非零退出映射；不包含会员、配额或与业务 ApplicationService 的耦合。</li>
 *   <li><b>结构化视图</b>：{@code versionInfo}/{@code *Submit} 等系列便捷方法与 {@link DreaminaCliTypedResult}
 *       在<strong>同一条执行链路</strong>上绑定原始 {@link DreaminaCliResult}；解析沉淀于 {@link DreaminaCliStructuredPayloadMapper}。</li>
 * </ul>
 * <pre>
 * Usage:
 *   dreamina [flags]
 *
 * 即梦 official AIGC CLI tool for login, account, and generation workflows
 *
 * About:
 *   dreamina is the 即梦 official AIGC CLI tool.
 *
 * Quick start:
 *   1. Run "dreamina login" to complete OAuth device login.
 *   2. For headless login, run "dreamina login --headless", then "dreamina login checklogin --device_code=<device_code>".
 *   3. Run a generator command such as "dreamina text2image --prompt=\"a cat portrait\"".
 *   4. Use "dreamina query_result --submit_id=<id>" for async tasks, or "dreamina list_task" to review saved tasks.
 *   5. Use "dreamina user_credit" to check the current account credit balance.
 *
 * Tips:
 *   Run "dreamina <subcommand> -h" to view detailed help for any subcommand.
 *   Login now uses OAuth Device Flow and prints verification_uri, user_code, and device_code in the terminal.
 *   All generation operations consume credits.
 *   Seedance 2.0 family is a flagship video generation model family and is a strong choice when output quality matters most.
 *
 * Built-in Commands:
 *   help                 Help about any command
 *   list_task            List saved tasks with status and result summary
 *   login                Log in locally with OAuth Device Flow before using task and account commands
 *   logout               Clear the local OAuth login state
 *   query_result         Query the current result of an async generation task
 *   relogin              Clear the local OAuth login state and force a fresh OAuth login
 *   session              Manage sessions (create/list/search/rename/delete)
 *   user_credit          Show the current user's remaining credit balance
 *   version              Print build version and commit information
 *
 *
 * Generator Commands:
 *   frames2video         Submit a Dreamina first-last-frames video task
 *   image2image          Submit a Dreamina image-to-image task
 *   image2video          Animate one image into video; use multiframe2video for multi-image stories
 *   image_upscale        Submit a Dreamina image upscale task
 *   multiframe2video     Create a coherent video story from multiple images
 *   multimodal2video     Dreamina flagship video mode (全能参考 / formerly ref2video) with all-around references and Seedance 2.0
 *   text2image           Submit a Dreamina text-to-image task
 *   text2video           Submit a Dreamina text-to-video task
 *
 *
 * Examples:
 *   dreamina login
 *   dreamina login --headless
 *   dreamina login checklogin --device_code=<device_code> --poll=30
 *   dreamina logout
 *   dreamina relogin
 *   dreamina user_credit
 *   dreamina list_task --gen_status=success
 *   dreamina query_result --submit_id=550e8400-e29b-41d4-a716-446655440000
 *   dreamina text2image --prompt="a cat portrait" --ratio=1:1 --resolution_type=2k
 * </pre>
 * @author wandl
 * @since 1.0.0
 */
@Slf4j
public class DreaminaCliExecutor {

    private final DreaminaCliProperties properties;

    /**
     * 将原始 CLI 快照映射为结构化负载（无框架强制注入，便于测试侧 {@code new DreaminaCliExecutor(props)}）。
     */
    private final DreaminaCliStructuredPayloadMapper structuredPayloadMapper = new DreaminaCliStructuredPayloadMapper();

    /**
     * 使用运行时配置构造执行器。
     *
     * @param properties CLI 路径、超时与工作目录等；不得为 null
     */
    public DreaminaCliExecutor(DreaminaCliProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    // -------------------------------------------------------------------------
    // 帮助（help）
    // -------------------------------------------------------------------------

    /**
     * 调用 {@code dreamina help} 打印总帮助或等价输出。
     *
     * @return 仅在进程零退出且无超时时返回；否则抛出统一的执行层异常
     */
    public DreaminaCliResult help() {
        return invoke(DreaminaCliSubcommands.Builtin.HELP, Collections.emptyList());
    }

    /**
     * 调用 {@code dreamina help <subcommand>}，查看指定一级子命令的说明（具体行为以 CLI 为准）。
     *
     * @param subcommand 一级子命令名，如 {@link DreaminaCliSubcommands.Image#TEXT2IMAGE}；不得为 null 或空白
     */
    public DreaminaCliResult help(String subcommand) {
        return help(subcommand, Collections.emptyList());
    }

    /**
     * 同上，并追加官方支持的额外参数片段。
     *
     * @param subcommand        子命令名；不得为 null 或空白
     * @param additionalRawArgs CLI 后缀参数，可为 null
     */
    public DreaminaCliResult help(String subcommand, List<String> additionalRawArgs) {
        Objects.requireNonNull(subcommand, "subcommand");
        if (DreaminaStrings.isBlank(subcommand)) {
            throw new IllegalArgumentException("subcommand must not be blank");
        }
        CommandLine cmd = newSubcommandChain(DreaminaCliSubcommands.Builtin.HELP, subcommand.trim());
        appendCleanArgs(cmd, additionalRawArgs);
        return run(cmd);
    }

    // -------------------------------------------------------------------------
    // 账号与会话（version / user_credit / login / logout / relogin / session）
    // -------------------------------------------------------------------------

    /**
     * 调用 {@code dreamina version} 查询本地 CLI 版本信息（通常为 JSON）。
     *
     * @return 仅在进程零退出且无超时时返回；否则抛出统一的执行层异常
     */
    public DreaminaCliResult version() {
        return invoke(DreaminaCliSubcommands.Account.VERSION, Collections.emptyList());
    }

    /**
     * 调用 {@code dreamina user_credit} 查询与用户额度相关的 CLI 原始输出。
     *
     * @return 仅在进程零退出且无超时时返回；否则抛出统一的执行层异常
     */
    public DreaminaCliResult userCredit() {
        return invoke(DreaminaCliSubcommands.Account.USER_CREDIT, Collections.emptyList());
    }

    /**
     * 调用 {@code dreamina login}（默认 OAuth 浏览器流程）。
     *
     * @return 仅在进程零退出且无超时时返回；否则抛出统一的执行层异常
     */
    public DreaminaCliResult login() {
        return login(Collections.emptyList());
    }

    /**
     * 调用 {@code dreamina login}，并追加官方支持的后缀参数（如 {@code --debug}、{@code --headless}）。
     *
     * @param additionalRawArgs CLI 片段列表，每项为单个 argv；null 视作空列表
     * @return 仅在进程零退出且无超时时返回；否则抛出统一的执行层异常
     */
    public DreaminaCliResult login(List<String> additionalRawArgs) {
        return invoke(DreaminaCliSubcommands.Account.LOGIN, additionalRawArgs);
    }

    /**
     * 调用 {@code dreamina login --headless}，进入无浏览器交互的设备码登录流程（后续常接
     * {@link #checkLogin(String, int, List)}）。
     *
     * @return 仅在进程零退出且无超时时返回；否则抛出统一的执行层异常
     */
    public DreaminaCliResult loginHeadless() {
        return loginHeadless(Collections.emptyList());
    }

    /**
     * 在 {@code --headless} 之后追加更多原生参数（如 {@code --debug}）。
     *
     * @param additionalRawArgs CLI 片段，在 {@code --headless} 之后追加；可为 null
     */
    public DreaminaCliResult loginHeadless(List<String> additionalRawArgs) {
        List<String> merged = new ArrayList<>();
        merged.add("--headless");
        if (additionalRawArgs != null) {
            for (String a : additionalRawArgs) {
                if (a != null && !a.trim().isEmpty()) {
                    merged.add(a);
                }
            }
        }
        return login(merged);
    }

    /**
     * 调用 {@code dreamina login checklogin --device_code=... --poll=...}，按设备码轮询完成 OAuth。
     *
     * @param deviceCode headless 流程返回的 device_code
     * @param pollSeconds 轮询间隔（秒），对应 {@code --poll=}
     */
    public DreaminaCliResult checkLogin(String deviceCode, int pollSeconds) {
        return checkLogin(deviceCode, pollSeconds, Collections.emptyList());
    }

    /**
     * 同上，并允许附加官方 flag（如将来 CLI 扩展的调试开关）。
     *
     * @param deviceCode        设备码；不得为 null
     * @param pollSeconds       轮询秒数；负值将抛 {@link IllegalArgumentException}
     * @param additionalRawArgs 额外 argv；可为 null
     */
    public DreaminaCliResult checkLogin(String deviceCode, int pollSeconds, List<String> additionalRawArgs) {
        Objects.requireNonNull(deviceCode, "deviceCode");
        if (DreaminaStrings.isBlank(deviceCode)) {
            throw new IllegalArgumentException("deviceCode must not be blank");
        }
        if (pollSeconds < 0) {
            throw new IllegalArgumentException("pollSeconds must be non-negative");
        }
        CommandLine cmd = newSubcommandChain(
            DreaminaCliSubcommands.Account.LOGIN, DreaminaCliSubcommands.LoginSub.CHECKLOGIN);
        appendQuotedKv(cmd, "--device_code", deviceCode.trim());
        cmd.addArgument("--poll=" + pollSeconds, false);
        appendCleanArgs(cmd, additionalRawArgs);
        return run(cmd);
    }

    /**
     * 调用 {@code dreamina logout}，清除凭证（保留 config 与本地任务库等行为以 CLI 为准）。
     *
     * @return 仅在进程零退出且无超时时返回；否则抛出统一的执行层异常
     */
    public DreaminaCliResult logout() {
        return logout(Collections.emptyList());
    }

    /**
     * 调用 {@code dreamina logout}，可附加额外原生参数。
     *
     * @param additionalRawArgs CLI 后缀参数，可为 null
     * @return 仅在进程零退出且无超时时返回；否则抛出统一的执行层异常
     */
    public DreaminaCliResult logout(List<String> additionalRawArgs) {
        return invoke(DreaminaCliSubcommands.Account.LOGOUT, additionalRawArgs);
    }

    /**
     * 调用 {@code dreamina relogin}，用于切换账号等场景。
     *
     * @return 仅在进程零退出且无超时时返回；否则抛出统一的执行层异常
     */
    public DreaminaCliResult relogin() {
        return relogin(Collections.emptyList());
    }

    /**
     * 调用 {@code dreamina relogin}，可附加额外原生参数。
     *
     * @param additionalRawArgs CLI 后缀参数，可为 null
     * @return 仅在进程零退出且无超时时返回；否则抛出统一的执行层异常
     */
    public DreaminaCliResult relogin(List<String> additionalRawArgs) {
        return invoke(DreaminaCliSubcommands.Account.RELOGIN, additionalRawArgs);
    }

    /**
     * 调用 {@code dreamina session}（无额外参数）。
     *
     * @return 仅在进程零退出且无超时时返回；否则抛出统一的执行层异常
     */
    public DreaminaCliResult session() {
        return session(Collections.emptyList());
    }

    /**
     * 调用 {@code dreamina session}，并追加子命令级参数。
     * <p>若仅需 {@code create/list/search/...} 中某一类操作，优先使用 {@link #sessionCreate(List)} 等显式方法。</p>
     *
     * @param additionalRawArgs CLI 后缀参数，可为 null
     * @return 仅在进程零退出且无超时时返回；否则抛出统一的执行层异常
     */
    public DreaminaCliResult session(List<String> additionalRawArgs) {
        return invoke(DreaminaCliSubcommands.Account.SESSION, additionalRawArgs);
    }

    /**
     * {@code dreamina session create}；无额外参数（等价于 {@link #sessionCreate(List)} 空列表）。
     */
    public DreaminaCliResult sessionCreate() {
        return sessionCreate(Collections.emptyList());
    }

    /**
     * {@code dreamina session create}；创建参数（名称、模型等）以官方 CLI 为准，通过 {@code additionalRawArgs} 传入。
     *
     * @param additionalRawArgs 子命令 {@code create} 之后的 flag/位置参数；可为 null
     */
    public DreaminaCliResult sessionCreate(List<String> additionalRawArgs) {
        return runSessionSub(DreaminaCliSubcommands.SessionSub.CREATE, null, null, additionalRawArgs);
    }

    /**
     * {@code dreamina session list}；无筛选参数。
     */
    public DreaminaCliResult sessionList() {
        return sessionList(Collections.emptyList());
    }

    /**
     * {@code dreamina session list}；筛选/分页等通过 {@code additionalRawArgs} 扩展。
     *
     * @param additionalRawArgs 可选 flag；可为 null
     */
    public DreaminaCliResult sessionList(List<String> additionalRawArgs) {
        return runSessionSub(DreaminaCliSubcommands.SessionSub.LIST, null, null, additionalRawArgs);
    }

    /**
     * {@code dreamina session search <searchTerm>}；无额外 flag。
     *
     * @param searchTerm 检索关键词；可为 null（与 {@link #sessionSearch(String, List)} 一致，null 时不追加位置参数）
     */
    public DreaminaCliResult sessionSearch(String searchTerm) {
        return sessionSearch(searchTerm, Collections.emptyList());
    }

    /**
     * {@code dreamina session search}。若 {@code searchTerm} 非空，在 {@code search} 子命令后追加一个 argv（常见为关键词；若官方仅支持 flag，可传 null 并在 {@code additionalRawArgs} 中写全量参数）。
     *
     * @param searchTerm          可选检索词；null 或空白则仅依赖 {@code additionalRawArgs}
     * @param additionalRawArgs 其它 flag；可为 null
     */
    public DreaminaCliResult sessionSearch(String searchTerm, List<String> additionalRawArgs) {
        return runSessionSub(DreaminaCliSubcommands.SessionSub.SEARCH, searchTerm, null, additionalRawArgs);
    }

    /**
     * {@code dreamina session rename <sessionId> <newName>}。
     */
    public DreaminaCliResult sessionRename(String sessionId, String newName) {
        return sessionRename(sessionId, newName, Collections.emptyList());
    }

    /**
     * {@code dreamina session rename <sessionId> <newName>}（后两项为独立 argv，含空格时由 Commons Exec 处理转义）。
     *
     * @param sessionId         当前会话标识；不得为 null/空白
     * @param newName           新显示名或标识；不得为 null/空白
     * @param additionalRawArgs 其它 flag；可为 null
     */
    public DreaminaCliResult sessionRename(String sessionId, String newName, List<String> additionalRawArgs) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(newName, "newName");
        if (DreaminaStrings.isBlank(sessionId) || DreaminaStrings.isBlank(newName)) {
            throw new IllegalArgumentException("sessionId and newName must not be blank");
        }
        return runSessionSub(
            DreaminaCliSubcommands.SessionSub.RENAME, sessionId.trim(), newName.trim(), additionalRawArgs);
    }

    /**
     * {@code dreamina session delete <sessionId>}。
     */
    public DreaminaCliResult sessionDelete(String sessionId) {
        return sessionDelete(sessionId, Collections.emptyList());
    }

    /**
     * {@code dreamina session delete <sessionId>}。
     *
     * @param sessionId         要删除的会话标识；不得为 null/空白
     * @param additionalRawArgs 如 {@code --force} 等扩展 flag；可为 null
     */
    public DreaminaCliResult sessionDelete(String sessionId, List<String> additionalRawArgs) {
        Objects.requireNonNull(sessionId, "sessionId");
        if (DreaminaStrings.isBlank(sessionId)) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        return runSessionSub(DreaminaCliSubcommands.SessionSub.DELETE, sessionId.trim(), null, additionalRawArgs);
    }

    /**
     * 拼装 {@code dreamina session &lt;verb&gt;} 及可选的一到两个位置参数后执行。
     */
    private DreaminaCliResult runSessionSub(
        String verb,
        String firstPositional,
        String secondPositional,
        List<String> additionalRawArgs) {
        Objects.requireNonNull(verb, "verb");
        CommandLine cmd = newSubcommandChain(DreaminaCliSubcommands.Account.SESSION, verb);
        if (DreaminaStrings.isNotBlank(firstPositional)) {
            cmd.addArgument(firstPositional.trim(), true);
        }
        if (DreaminaStrings.isNotBlank(secondPositional)) {
            cmd.addArgument(secondPositional.trim(), true);
        }
        appendCleanArgs(cmd, additionalRawArgs);
        return run(cmd);
    }

    // -------------------------------------------------------------------------
    // 图片生成（text2image / image2image / image_upscale）
    // -------------------------------------------------------------------------

    /**
     * 调用 {@code dreamina text2image --prompt=...} 触发文本到图像任务。
     *
     * @param prompt 必填提示词
     */
    public DreaminaCliResult text2Image(String prompt) {
        return text2Image(prompt, Collections.emptyList());
    }

    /**
     * 同上，附带额外原生参数片段（每项按单个 argv 传入，不做 shell 拆分）。
     *
     * @param prompt            必填提示词
     * @param additionalRawArgs CLI 后缀参数，可为 null 视作空列表
     */
    public DreaminaCliResult text2Image(String prompt, List<String> additionalRawArgs) {
        Objects.requireNonNull(prompt, "prompt");
        return runWithPromptFlag(DreaminaCliSubcommands.Image.TEXT2IMAGE, prompt, additionalRawArgs);
    }

    /**
     * 使用强类型请求对象调用 {@code dreamina text2image}。
     * <p>
     * 将 Jimeng 技能中沉淀的 ratio / model / resolution / session / poll 约束固定在请求模型中，
     * 避免上层重复手写原始 flag。
     * </p>
     *
     * @param request 文生图请求；不得为 null
     * @return CLI 原始执行快照
     */
    public DreaminaCliResult text2Image(DreaminaText2ImageRequest request) {
        Objects.requireNonNull(request, "request");
        return invoke(DreaminaCliSubcommands.Image.TEXT2IMAGE, request.toCliArgs());
    }

    /**
     * 调用 {@code dreamina image2image}：图生图，需传入参考图列表与编辑提示词。
     * <p>参考图写法与官方 CLI 一致，通常为逗号分隔的本地路径列表（1–10 张）。</p>
     *
     * @param imagesCsv         {@code --images=} 取值，必填
     * @param prompt            {@code --prompt=} 取值，必填
     * @param additionalRawArgs 其它 flag（如 {@code --model_version=}、{@code --poll=}），可为 null
     */
    public DreaminaCliResult image2Image(String imagesCsv, String prompt, List<String> additionalRawArgs) {
        Objects.requireNonNull(imagesCsv, "imagesCsv");
        Objects.requireNonNull(prompt, "prompt");
        CommandLine cmd = newSubcommand(DreaminaCliSubcommands.Image.IMAGE2IMAGE);
        appendQuotedKv(cmd, "--images", imagesCsv);
        appendQuotedKv(cmd, "--prompt", prompt);
        appendCleanArgs(cmd, additionalRawArgs);
        return run(cmd);
    }

    /**
     * 使用强类型请求对象调用 {@code dreamina image2image}。
     *
     * @param request 图生图请求；不得为 null
     * @return CLI 原始执行快照
     */
    public DreaminaCliResult image2Image(DreaminaImage2ImageRequest request) {
        Objects.requireNonNull(request, "request");
        return invoke(DreaminaCliSubcommands.Image.IMAGE2IMAGE, request.toCliArgs());
    }

    /**
     * {@code dreamina image_upscale}，无附加参数。
     */
    public DreaminaCliResult imageUpscale() {
        return imageUpscale(Collections.emptyList());
    }

    /**
     * 调用 {@code dreamina image_upscale}；具体必填参数由调用方在 {@code additionalRawArgs} 中给出。
     * <p>执行器仅负责子命令与通用执行语义，避免对上游模型参数做强假设。</p>
     *
     * @param additionalRawArgs CLI 参数列表；若为 null 则仅执行子命令本身（通常不足以完成放大，仅供参考）
     */
    public DreaminaCliResult imageUpscale(List<String> additionalRawArgs) {
        return invoke(DreaminaCliSubcommands.Image.IMAGE_UPSCALE, additionalRawArgs);
    }

    /**
     * 使用强类型请求对象调用 {@code dreamina image_upscale}。
     *
     * @param request 图像超分请求；不得为 null
     * @return CLI 原始执行快照
     */
    public DreaminaCliResult imageUpscale(DreaminaImageUpscaleRequest request) {
        Objects.requireNonNull(request, "request");
        return invoke(DreaminaCliSubcommands.Image.IMAGE_UPSCALE, request.toCliArgs());
    }

    // -------------------------------------------------------------------------
    // 视频生成（text2video / image2video / frames2video / multiframe2video / multimodal2video）
    // -------------------------------------------------------------------------

    /**
     * 调用 {@code dreamina text2video --prompt=...} 触发文生视频。
     *
     * @param prompt 必填提示词
     */
    public DreaminaCliResult text2video(String prompt) {
        return text2video(prompt, Collections.emptyList());
    }

    /**
     * 文生视频并附加额外原生参数（如 {@code --duration=}、{@code --model_version=}、{@code --poll=}）。
     *
     * @param prompt            必填提示词
     * @param additionalRawArgs CLI 后缀参数，可为 null
     */
    public DreaminaCliResult text2video(String prompt, List<String> additionalRawArgs) {
        Objects.requireNonNull(prompt, "prompt");
        return runWithPromptFlag(DreaminaCliSubcommands.Video.TEXT2VIDEO, prompt, additionalRawArgs);
    }

    /**
     * 使用强类型请求对象调用 {@code dreamina text2video}。
     *
     * @param request 文生视频请求；不得为 null
     * @return CLI 原始执行快照
     */
    public DreaminaCliResult text2video(DreaminaText2VideoRequest request) {
        Objects.requireNonNull(request, "request");
        return invoke(DreaminaCliSubcommands.Video.TEXT2VIDEO, request.toCliArgs());
    }

    /**
     * 调用 {@code dreamina image2video}：仅参考图、不传 {@code --prompt=}（官方允许时用于默认动画语义）。
     *
     * @param imagePath         {@code --image=} 本地路径，必填
     * @param additionalRawArgs 其它 flag；可为 null
     */
    public DreaminaCliResult image2video(String imagePath, List<String> additionalRawArgs) {
        return image2video(imagePath, null, additionalRawArgs);
    }

    /**
     * 调用 {@code dreamina image2video}：单张参考图驱动视频。
     *
     * @param imagePath         {@code --image=} 本地路径，必填
     * @param prompt            {@code --prompt=}；若为空或空白则省略该参数（与官方可选语义一致）
     * @param additionalRawArgs 其它 flag；可为 null
     */
    public DreaminaCliResult image2video(String imagePath, String prompt, List<String> additionalRawArgs) {
        Objects.requireNonNull(imagePath, "imagePath");
        CommandLine cmd = newSubcommand(DreaminaCliSubcommands.Video.IMAGE2VIDEO);
        appendQuotedKv(cmd, "--image", imagePath);
        if (DreaminaStrings.isNotBlank(prompt)) {
            appendQuotedKv(cmd, "--prompt", prompt);
        }
        appendCleanArgs(cmd, additionalRawArgs);
        return run(cmd);
    }

    /**
     * 使用强类型请求对象调用 {@code dreamina image2video}。
     *
     * @param request 单图生视频请求；不得为 null
     * @return CLI 原始执行快照
     */
    public DreaminaCliResult image2video(DreaminaImage2VideoRequest request) {
        Objects.requireNonNull(request, "request");
        return invoke(DreaminaCliSubcommands.Video.IMAGE2VIDEO, request.toCliArgs());
    }

    /**
     * {@code dreamina frames2video}，参数均由 CLI 交互或后续调用方补充时使用空列表。
     */
    public DreaminaCliResult frames2video() {
        return frames2video(Collections.emptyList());
    }

    /**
     * {@code dreamina frames2video}：首尾帧过渡；必填参数放在 {@code additionalRawArgs}（如 {@code --first=} / {@code --last=}）。
     */
    public DreaminaCliResult frames2video(List<String> additionalRawArgs) {
        return invoke(DreaminaCliSubcommands.Video.FRAMES2VIDEO, additionalRawArgs);
    }

    /**
     * 使用强类型请求对象调用 {@code dreamina frames2video}。
     *
     * @param request 首尾帧视频请求；不得为 null
     * @return CLI 原始执行快照
     */
    public DreaminaCliResult frames2video(DreaminaFrames2VideoRequest request) {
        Objects.requireNonNull(request, "request");
        return invoke(DreaminaCliSubcommands.Video.FRAMES2VIDEO, request.toCliArgs());
    }

    /**
     * {@code dreamina multiframe2video}，无附加参数。
     */
    public DreaminaCliResult multiframe2video() {
        return multiframe2video(Collections.emptyList());
    }

    /**
     * {@code dreamina multiframe2video}：多分镜图叙事；必填参数放在 {@code additionalRawArgs}。
     */
    public DreaminaCliResult multiframe2video(List<String> additionalRawArgs) {
        return invoke(DreaminaCliSubcommands.Video.MULTIFRAME2VIDEO, additionalRawArgs);
    }

    /**
     * 使用强类型请求对象调用 {@code dreamina multiframe2video}。
     *
     * @param request 多帧故事视频请求；不得为 null
     * @return CLI 原始执行快照
     */
    public DreaminaCliResult multiframe2video(DreaminaMultiframe2VideoRequest request) {
        Objects.requireNonNull(request, "request");
        return invoke(DreaminaCliSubcommands.Video.MULTIFRAME2VIDEO, request.toCliArgs());
    }

    /**
     * {@code dreamina multimodal2video}，无附加参数。
     */
    public DreaminaCliResult multimodal2video() {
        return multimodal2video(Collections.emptyList());
    }

    /**
     * {@code dreamina multimodal2video}：多模态合成；必填参数放在 {@code additionalRawArgs}。
     */
    public DreaminaCliResult multimodal2video(List<String> additionalRawArgs) {
        return invoke(DreaminaCliSubcommands.Video.MULTIMODAL2VIDEO, additionalRawArgs);
    }

    /**
     * 使用强类型请求对象调用 {@code dreamina multimodal2video}。
     *
     * @param request 多模态视频请求；不得为 null
     * @return CLI 原始执行快照
     */
    public DreaminaCliResult multimodal2video(DreaminaMultimodal2VideoRequest request) {
        Objects.requireNonNull(request, "request");
        return invoke(DreaminaCliSubcommands.Video.MULTIMODAL2VIDEO, request.toCliArgs());
    }

    // -------------------------------------------------------------------------
    // 任务查询（query_result / list_task）
    // -------------------------------------------------------------------------

    /**
     * 调用 {@code dreamina query_result --submit_id=...} 查询任务状态或产物信息。
     *
     * @param submitId Dreamina 侧提交编号
     */
    public DreaminaCliResult queryResult(String submitId) {
        return queryResult(submitId, Collections.emptyList());
    }

    /**
     * 同上，并追加额外原生参数。
     *
     * @param submitId          提交编号；不得为 null
     * @param additionalRawArgs 扩展 flag；可为 null
     */
    public DreaminaCliResult queryResult(String submitId, List<String> additionalRawArgs) {
        Objects.requireNonNull(submitId, "submitId");
        CommandLine cmd = newSubcommand(DreaminaCliSubcommands.Task.QUERY_RESULT);
        appendQuotedKv(cmd, "--submit_id", submitId);
        appendCleanArgs(cmd, additionalRawArgs);
        return run(cmd);
    }

    /**
     * 使用强类型请求对象调用 {@code dreamina query_result}。
     *
     * @param request 查询请求；不得为 null
     * @return CLI 原始执行快照
     */
    public DreaminaCliResult queryResult(DreaminaQueryResultRequest request) {
        Objects.requireNonNull(request, "request");
        return invoke(DreaminaCliSubcommands.Task.QUERY_RESULT, request.toCliArgs());
    }

    /**
     * 调用 {@code dreamina list_task} 枚举任务列表。
     *
     * @return CLI 聚合后的标准输出/错误快照
     */
    public DreaminaCliResult listTask() {
        return listTask(Collections.emptyList());
    }

    /**
     * {@code dreamina list_task} 并附加筛选参数（如 {@code --gen_status=success}）。
     *
     * @param additionalRawArgs 官方支持的 flag；可为 null
     */
    public DreaminaCliResult listTask(List<String> additionalRawArgs) {
        return invoke(DreaminaCliSubcommands.Task.LIST_TASK, additionalRawArgs);
    }

    /**
     * 使用强类型请求对象调用 {@code dreamina list_task}。
     *
     * @param request 列表筛选请求；不得为 null
     * @return CLI 原始执行快照
     */
    public DreaminaCliResult listTask(DreaminaListTaskRequest request) {
        Objects.requireNonNull(request, "request");
        return invoke(DreaminaCliSubcommands.Task.LIST_TASK, request.toCliArgs());
    }

    // -------------------------------------------------------------------------
    // 结构化便捷封装（在保留原始 {@link DreaminaCliResult} 之上提供强类型视图）
    // -------------------------------------------------------------------------

    /**
     * {@link #version()} 的结构化视图。
     *
     * @return 绑定原始快照与 {@link DreaminaVersionResult}
     */
    public DreaminaCliTypedResult<DreaminaVersionResult> versionInfo() {
        DreaminaCliResult raw = version();
        return DreaminaCliTypedResult.of(raw, structuredPayloadMapper.mapVersion(raw));
    }

    /**
     * {@link #userCredit()} 的结构化视图。
     */
    public DreaminaCliTypedResult<DreaminaUserCreditResult> userCreditInfo() {
        DreaminaCliResult raw = userCredit();
        return DreaminaCliTypedResult.of(raw, structuredPayloadMapper.mapUserCredit(raw));
    }

    /**
     * {@link #help()} 的结构化视图。
     */
    public DreaminaCliTypedResult<DreaminaHelpResult> helpInfo() {
        DreaminaCliResult raw = help();
        return DreaminaCliTypedResult.of(raw, structuredPayloadMapper.mapHelp(null, raw));
    }

    /**
     * {@link #help(String)} 的结构化视图。
     *
     * @param subcommand 目标子命令名
     */
    public DreaminaCliTypedResult<DreaminaHelpResult> helpInfo(String subcommand) {
        DreaminaCliResult raw = help(subcommand);
        return DreaminaCliTypedResult.of(raw, structuredPayloadMapper.mapHelp(subcommand, raw));
    }

    /**
     * {@link #help(String, List)} 的结构化视图。
     *
     * @param subcommand        目标子命令名
     * @param additionalRawArgs 追加原生参数
     */
    public DreaminaCliTypedResult<DreaminaHelpResult> helpInfo(String subcommand, List<String> additionalRawArgs) {
        DreaminaCliResult raw = help(subcommand, additionalRawArgs);
        return DreaminaCliTypedResult.of(raw, structuredPayloadMapper.mapHelp(subcommand, raw));
    }

    /**
     * {@link #loginHeadless()} 的结构化视图：可能是 Device Flow JSON，也可能仅为「复用本地 OAuth」提示文本。
     */
    public DreaminaCliTypedResult<DreaminaLoginResult> loginHeadlessInfo() {
        DreaminaCliResult raw = loginHeadless();
        return DreaminaCliTypedResult.of(raw, structuredPayloadMapper.mapLogin(raw));
    }

    /**
     * {@link #loginHeadless(List)} 的结构化视图。
     *
     * @param additionalRawArgs headless 后缀参数
     */
    public DreaminaCliTypedResult<DreaminaLoginResult> loginHeadlessInfo(List<String> additionalRawArgs) {
        DreaminaCliResult raw = loginHeadless(additionalRawArgs);
        return DreaminaCliTypedResult.of(raw, structuredPayloadMapper.mapLogin(raw));
    }

    /**
     * 显式解析 Device Flow JSON（若 stdout 非 JSON 则字段为空）。
     */
    public DreaminaCliTypedResult<DreaminaDeviceLoginResult> deviceLoginMaterial(DreaminaCliResult loginStdOutSnapshot) {
        DreaminaDeviceLoginResult dto = structuredPayloadMapper.mapDeviceLogin(loginStdOutSnapshot);
        return DreaminaCliTypedResult.of(loginStdOutSnapshot, dto);
    }

    /**
     * {@link #sessionList()} 的结构化视图。
     */
    public DreaminaCliTypedResult<DreaminaSessionListResult> sessionListInfo() {
        DreaminaCliResult raw = sessionList();
        return DreaminaCliTypedResult.of(raw, structuredPayloadMapper.mapSessionList(raw));
    }

    /**
     * {@link #sessionList(List)} 的结构化视图。
     *
     * @param additionalRawArgs 透传到 CLI 的 flag
     */
    public DreaminaCliTypedResult<DreaminaSessionListResult> sessionListInfo(List<String> additionalRawArgs) {
        DreaminaCliResult raw = sessionList(additionalRawArgs);
        return DreaminaCliTypedResult.of(raw, structuredPayloadMapper.mapSessionList(raw));
    }

    /**
     * {@link #sessionSearch(String)} 的结构化视图。
     *
     * @param searchTerm 检索关键字；可为 null（等同底层 CLI 语义）
     */
    public DreaminaCliTypedResult<DreaminaSessionSearchResult> sessionSearchInfo(String searchTerm) {
        DreaminaCliResult raw = sessionSearch(searchTerm);
        return DreaminaCliTypedResult.of(raw, structuredPayloadMapper.mapSessionSearch(searchTerm, raw));
    }

    /**
     * {@link #sessionSearch(String, List)} 的结构化视图。
     */
    public DreaminaCliTypedResult<DreaminaSessionSearchResult> sessionSearchInfo(
        String searchTerm, List<String> additionalRawArgs) {
        DreaminaCliResult raw = sessionSearch(searchTerm, additionalRawArgs);
        return DreaminaCliTypedResult.of(raw, structuredPayloadMapper.mapSessionSearch(searchTerm, raw));
    }

    /**
     * {@link #sessionCreate()} 的结构化视图。
     */
    public DreaminaCliTypedResult<DreaminaSessionMutationResult> sessionCreateInfo() {
        DreaminaCliResult raw = sessionCreate();
        return DreaminaCliTypedResult.of(raw, structuredPayloadMapper.mapSessionMutation(raw));
    }

    /**
     * {@link #sessionCreate(List)} 的结构化视图。
     *
     * @param additionalRawArgs 会话名称或其它官方 flag
     */
    public DreaminaCliTypedResult<DreaminaSessionMutationResult> sessionCreateInfo(List<String> additionalRawArgs) {
        DreaminaCliResult raw = sessionCreate(additionalRawArgs);
        return DreaminaCliTypedResult.of(raw, structuredPayloadMapper.mapSessionMutation(raw));
    }

    /**
     * {@link #sessionRename(String, String)} 的结构化视图。
     */
    public DreaminaCliTypedResult<DreaminaSessionMutationResult> sessionRenameInfo(String sessionId, String newName) {
        DreaminaCliResult raw = sessionRename(sessionId, newName);
        return DreaminaCliTypedResult.of(raw, structuredPayloadMapper.mapSessionMutation(raw));
    }

    /**
     * {@link #sessionRename(String, String, List)} 的结构化视图。
     */
    public DreaminaCliTypedResult<DreaminaSessionMutationResult> sessionRenameInfo(
        String sessionId, String newName, List<String> additionalRawArgs) {
        DreaminaCliResult raw = sessionRename(sessionId, newName, additionalRawArgs);
        return DreaminaCliTypedResult.of(raw, structuredPayloadMapper.mapSessionMutation(raw));
    }

    /**
     * {@link #listTask()} 的结构化视图。
     */
    public DreaminaCliTypedResult<DreaminaTaskListResult> listTaskInfo() {
        DreaminaCliResult raw = listTask();
        return DreaminaCliTypedResult.of(raw, structuredPayloadMapper.mapTaskList(raw));
    }

    /**
     * {@link #listTask(List)} 的结构化视图。
     */
    public DreaminaCliTypedResult<DreaminaTaskListResult> listTaskInfo(List<String> additionalRawArgs) {
        DreaminaCliResult raw = listTask(additionalRawArgs);
        return DreaminaCliTypedResult.of(raw, structuredPayloadMapper.mapTaskList(raw));
    }

    /**
     * {@link #listTask(DreaminaListTaskRequest)} 的结构化视图。
     */
    public DreaminaCliTypedResult<DreaminaTaskListResult> listTaskInfo(DreaminaListTaskRequest request) {
        DreaminaCliResult raw = listTask(request);
        return DreaminaCliTypedResult.of(raw, structuredPayloadMapper.mapTaskList(raw));
    }

    /**
     * {@link #queryResult(String)} 的结构化视图。
     *
     * @param submitId 提交编号
     */
    public DreaminaCliTypedResult<DreaminaQueryResult> queryResultInfo(String submitId) {
        DreaminaCliResult raw = queryResult(submitId);
        return DreaminaCliTypedResult.of(raw, structuredPayloadMapper.mapQueryResult(raw));
    }

    /**
     * {@link #queryResult(String, List)} 的结构化视图。
     */
    public DreaminaCliTypedResult<DreaminaQueryResult> queryResultInfo(String submitId, List<String> additionalRawArgs) {
        DreaminaCliResult raw = queryResult(submitId, additionalRawArgs);
        return DreaminaCliTypedResult.of(raw, structuredPayloadMapper.mapQueryResult(raw));
    }

    /**
     * {@link #queryResult(DreaminaQueryResultRequest)} 的结构化视图。
     */
    public DreaminaCliTypedResult<DreaminaQueryResult> queryResultInfo(DreaminaQueryResultRequest request) {
        DreaminaCliResult raw = queryResult(request);
        return DreaminaCliTypedResult.of(raw, structuredPayloadMapper.mapQueryResult(raw));
    }

    /**
     * {@link #text2Image(String, List)} 的结构化提交视图。
     */
    public DreaminaCliTypedResult<DreaminaGenerateSubmitResult> text2ImageSubmit(String prompt, List<String> additionalRawArgs) {
        DreaminaCliResult raw = text2Image(prompt, additionalRawArgs);
        return DreaminaCliTypedResult.of(raw, structuredPayloadMapper.mapGenerateSubmit(raw));
    }

    /**
     * {@link #text2Image(DreaminaText2ImageRequest)} 的结构化提交视图。
     *
     * @param request 文生图请求
     * @return 原始快照与结构化提交结果
     */
    public DreaminaCliTypedResult<DreaminaGenerateSubmitResult> text2ImageSubmit(DreaminaText2ImageRequest request) {
        DreaminaCliResult raw = text2Image(request);
        return DreaminaCliTypedResult.of(raw, structuredPayloadMapper.mapGenerateSubmit(raw));
    }

    /**
     * {@link #image2Image(String, String, List)} 的结构化提交视图。
     */
    public DreaminaCliTypedResult<DreaminaGenerateSubmitResult> image2ImageSubmit(
        String imagesCsv, String prompt, List<String> additionalRawArgs) {
        DreaminaCliResult raw = image2Image(imagesCsv, prompt, additionalRawArgs);
        return DreaminaCliTypedResult.of(raw, structuredPayloadMapper.mapGenerateSubmit(raw));
    }

    /**
     * {@link #image2Image(DreaminaImage2ImageRequest)} 的结构化提交视图。
     *
     * @param request 图生图请求
     * @return 原始快照与结构化提交结果
     */
    public DreaminaCliTypedResult<DreaminaGenerateSubmitResult> image2ImageSubmit(DreaminaImage2ImageRequest request) {
        DreaminaCliResult raw = image2Image(request);
        return DreaminaCliTypedResult.of(raw, structuredPayloadMapper.mapGenerateSubmit(raw));
    }

    /**
     * {@link #imageUpscale(List)} 的结构化提交视图。
     */
    public DreaminaCliTypedResult<DreaminaGenerateSubmitResult> imageUpscaleSubmit(List<String> additionalRawArgs) {
        DreaminaCliResult raw = imageUpscale(additionalRawArgs);
        return DreaminaCliTypedResult.of(raw, structuredPayloadMapper.mapGenerateSubmit(raw));
    }

    /**
     * {@link #imageUpscale(DreaminaImageUpscaleRequest)} 的结构化提交视图。
     *
     * @param request 图像超分请求
     * @return 原始快照与结构化提交结果
     */
    public DreaminaCliTypedResult<DreaminaGenerateSubmitResult> imageUpscaleSubmit(DreaminaImageUpscaleRequest request) {
        DreaminaCliResult raw = imageUpscale(request);
        return DreaminaCliTypedResult.of(raw, structuredPayloadMapper.mapGenerateSubmit(raw));
    }

    /**
     * {@link #text2video(String, List)} 的结构化提交视图。
     */
    public DreaminaCliTypedResult<DreaminaGenerateSubmitResult> text2VideoSubmit(String prompt, List<String> additionalRawArgs) {
        DreaminaCliResult raw = text2video(prompt, additionalRawArgs);
        return DreaminaCliTypedResult.of(raw, structuredPayloadMapper.mapGenerateSubmit(raw));
    }

    /**
     * {@link #text2video(DreaminaText2VideoRequest)} 的结构化提交视图。
     *
     * @param request 文生视频请求
     * @return 原始快照与结构化提交结果
     */
    public DreaminaCliTypedResult<DreaminaGenerateSubmitResult> text2VideoSubmit(DreaminaText2VideoRequest request) {
        DreaminaCliResult raw = text2video(request);
        return DreaminaCliTypedResult.of(raw, structuredPayloadMapper.mapGenerateSubmit(raw));
    }

    /**
     * {@link #image2video(String, String, List)} 的结构化提交视图。
     */
    public DreaminaCliTypedResult<DreaminaGenerateSubmitResult> image2VideoSubmit(
        String imagePath, String prompt, List<String> additionalRawArgs) {
        DreaminaCliResult raw = image2video(imagePath, prompt, additionalRawArgs);
        return DreaminaCliTypedResult.of(raw, structuredPayloadMapper.mapGenerateSubmit(raw));
    }

    /**
     * {@link #image2video(DreaminaImage2VideoRequest)} 的结构化提交视图。
     *
     * @param request 单图生视频请求
     * @return 原始快照与结构化提交结果
     */
    public DreaminaCliTypedResult<DreaminaGenerateSubmitResult> image2VideoSubmit(DreaminaImage2VideoRequest request) {
        DreaminaCliResult raw = image2video(request);
        return DreaminaCliTypedResult.of(raw, structuredPayloadMapper.mapGenerateSubmit(raw));
    }

    /**
     * {@link #frames2video(List)} 的结构化提交视图。
     */
    public DreaminaCliTypedResult<DreaminaGenerateSubmitResult> frames2VideoSubmit(List<String> additionalRawArgs) {
        DreaminaCliResult raw = frames2video(additionalRawArgs);
        return DreaminaCliTypedResult.of(raw, structuredPayloadMapper.mapGenerateSubmit(raw));
    }

    /**
     * {@link #frames2video(DreaminaFrames2VideoRequest)} 的结构化提交视图。
     *
     * @param request 首尾帧视频请求
     * @return 原始快照与结构化提交结果
     */
    public DreaminaCliTypedResult<DreaminaGenerateSubmitResult> frames2VideoSubmit(DreaminaFrames2VideoRequest request) {
        DreaminaCliResult raw = frames2video(request);
        return DreaminaCliTypedResult.of(raw, structuredPayloadMapper.mapGenerateSubmit(raw));
    }

    /**
     * {@link #multiframe2video(List)} 的结构化提交视图。
     */
    public DreaminaCliTypedResult<DreaminaGenerateSubmitResult> multiframe2VideoSubmit(List<String> additionalRawArgs) {
        DreaminaCliResult raw = multiframe2video(additionalRawArgs);
        return DreaminaCliTypedResult.of(raw, structuredPayloadMapper.mapGenerateSubmit(raw));
    }

    /**
     * {@link #multiframe2video(DreaminaMultiframe2VideoRequest)} 的结构化提交视图。
     *
     * @param request 多帧故事视频请求
     * @return 原始快照与结构化提交结果
     */
    public DreaminaCliTypedResult<DreaminaGenerateSubmitResult> multiframe2VideoSubmit(
        DreaminaMultiframe2VideoRequest request) {
        DreaminaCliResult raw = multiframe2video(request);
        return DreaminaCliTypedResult.of(raw, structuredPayloadMapper.mapGenerateSubmit(raw));
    }

    /**
     * {@link #multimodal2video(List)} 的结构化提交视图。
     */
    public DreaminaCliTypedResult<DreaminaGenerateSubmitResult> multimodal2VideoSubmit(List<String> additionalRawArgs) {
        DreaminaCliResult raw = multimodal2video(additionalRawArgs);
        return DreaminaCliTypedResult.of(raw, structuredPayloadMapper.mapGenerateSubmit(raw));
    }

    /**
     * {@link #multimodal2video(DreaminaMultimodal2VideoRequest)} 的结构化提交视图。
     *
     * @param request 多模态视频请求
     * @return 原始快照与结构化提交结果
     */
    public DreaminaCliTypedResult<DreaminaGenerateSubmitResult> multimodal2VideoSubmit(
        DreaminaMultimodal2VideoRequest request) {
        DreaminaCliResult raw = multimodal2video(request);
        return DreaminaCliTypedResult.of(raw, structuredPayloadMapper.mapGenerateSubmit(raw));
    }

    /**
     * 通用逃逸口：在执行任意子命令后映射为「生成提交」视图（若 JSON 不匹配则字段多为空）。
     *
     * @param raw 事先取得的 CLI 快照；不得为 null
     */
    public DreaminaCliTypedResult<DreaminaGenerateSubmitResult> mapGenerateSubmitOnly(DreaminaCliResult raw) {
        return DreaminaCliTypedResult.of(raw, structuredPayloadMapper.mapGenerateSubmit(raw));
    }

    /**
     * 通用：映射 {@link DreaminaQueryResult}。
     *
     * @param raw 事先取得的 CLI 快照；不得为 null
     */
    public DreaminaCliTypedResult<DreaminaQueryResult> mapQueryResultOnly(DreaminaCliResult raw) {
        return DreaminaCliTypedResult.of(raw, structuredPayloadMapper.mapQueryResult(raw));
    }

    /**
     * 通用：映射 {@link DreaminaTaskListResult}。
     *
     * @param raw 事先取得的 CLI 快照；不得为 null
     */
    public DreaminaCliTypedResult<DreaminaTaskListResult> mapTaskListOnly(DreaminaCliResult raw) {
        return DreaminaCliTypedResult.of(raw, structuredPayloadMapper.mapTaskList(raw));
    }

    /**
     * 通用：映射 {@link DreaminaHelpResult}。
     *
     * @param topic 帮助主题；可为 null
     * @param raw   CLI 快照；不得为 null
     */
    public DreaminaCliTypedResult<DreaminaHelpResult> mapHelpOnly(String topic, DreaminaCliResult raw) {
        return DreaminaCliTypedResult.of(raw, structuredPayloadMapper.mapHelp(topic, raw));
    }

    /**
     * 暴露底层映射器：便于上层自定义组合或在测试中替换策略。
     *
     * @return 非 null 的默认映射器实例
     */
    public DreaminaCliStructuredPayloadMapper structuredPayloadMapper() {
        return structuredPayloadMapper;
    }

    /**
     * 通用逃逸口：追加任意Dreamina支持的「一级子命令」及后续 argv。
     * <p>适用于官方 CLI 先于本模块增加新 capability 的场景。</p>
     *
     * @param subcommand        一级子命令名（如 {@code DreaminaCliSubcommands.Image#TEXT2IMAGE}），不得为空
     * @param additionalRawArgs 子命令之后的参数；可为 null
     */
    public DreaminaCliResult invoke(String subcommand, List<String> additionalRawArgs) {
        Objects.requireNonNull(subcommand, "subcommand");
        if (DreaminaStrings.isBlank(subcommand)) {
            throw new IllegalArgumentException("subcommand must not be blank");
        }
        CommandLine cmd = newSubcommand(subcommand.trim());
        appendCleanArgs(cmd, additionalRawArgs);
        return run(cmd);
    }

    /**
     * 基于配置拼装可执行的 {@link CommandLine} 根命令。
     */
    private CommandLine baseCommandLine() {
        return new CommandLine(properties.getExecutable());
    }

    /**
     * 在根可执行名下追加一级子命令；返回的同一条命令行可继续挂载 flag。
     */
    private CommandLine newSubcommand(String subcommand) {
        return newSubcommandChain(subcommand);
    }

    /**
     * 追加从子命令起的连续 argv 段（不含可执行文件路径），用于 {@code dreamina login checklogin}、
     * {@code dreamina session create} 等多级子命令。
     *
     * @param subcommandTokens 至少一段，每段为无空白的子命令 token
     */
    CommandLine newSubcommandChain(String... subcommandTokens) {
        if (subcommandTokens == null || subcommandTokens.length == 0) {
            throw new IllegalArgumentException("subcommandTokens must be non-empty");
        }
        CommandLine cmd = baseCommandLine();
        for (String token : subcommandTokens) {
            if (DreaminaStrings.isBlank(token)) {
                throw new IllegalArgumentException("subcommand token must not be null/blank");
            }
            cmd.addArgument(token.trim());
        }
        return cmd;
    }

    /**
     * 文生图 / 文生视频等共用 {@code --prompt=} 拼装逻辑，减少重复。
     */
    private DreaminaCliResult runWithPromptFlag(String subcommand, String prompt, List<String> additionalRawArgs) {
        CommandLine cmd = newSubcommand(subcommand);
        appendQuotedKv(cmd, "--prompt", prompt);
        appendCleanArgs(cmd, additionalRawArgs);
        return run(cmd);
    }

    /**
     * 组装 {@code --key=value} 形式参数，{@code handleQuoting=true} 以避免空格或 shell 特殊字符问题。
     */
    private static void appendQuotedKv(CommandLine cmd, String key, String value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        if (!key.startsWith("--")) {
            throw new IllegalArgumentException("CLI key must start with '--', got: " + key);
        }
        String prefix = key.endsWith("=") ? key.substring(0, key.length() - 1) : key;
        cmd.addArgument(prefix + "=" + value, true);
    }

    /**
     * 过滤空白项后逐个追加 CLI 片段。
     */
    private static void appendCleanArgs(CommandLine cmd, List<String> args) {
        if (args == null || args.isEmpty()) {
            return;
        }
        for (String a : args) {
            if (a != null && !a.trim().isEmpty()) {
                cmd.addArgument(a, false);
            }
        }
    }

    /**
     * 以 Commons Exec + Watchdog 执行命令行，并完成统一的结果与异常语义。
     *
     * <p>Commons Exec 1.4 仍将 {@link DefaultExecutor}、{@link ExecuteWatchdog} 部分入口标为过时；待上游稳定替代 API 后可统一迁移。</p>
     */
    @SuppressWarnings("deprecation")
    private DreaminaCliResult run(CommandLine commandLine) {
        long timeoutMs = properties.getCommandTimeoutMillis();
        if (timeoutMs <= 0) {
            throw new IllegalStateException("dreamina.cli.command-timeout-millis must be positive");
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        DefaultExecutor executor = newRunExecutor();
        executor.setStreamHandler(new PumpStreamHandler(out, err));

        // --- 可选工作目录：非法路径在使用前即失败 ---
        String wdProperty = properties.getWorkingDirectory();
        if (wdProperty != null && !wdProperty.trim().isEmpty()) {
            File wd = new File(wdProperty.trim());
            if (!wd.isDirectory()) {
                throw new DreaminaCliExecutableFailureException(
                    "dreamina.cli.working-directory is not an existing directory: " + wd.getAbsolutePath(), null);
            }
            executor.setWorkingDirectory(wd);
        }

        ExecuteWatchdog watchdog = new ExecuteWatchdog(timeoutMs);
        executor.setWatchdog(watchdog);
        DefaultExecuteResultHandler handler = new DefaultExecuteResultHandler();

        // --- 启动子进程并阻塞等待收尾 ---
        try {
            executor.execute(commandLine, handler);
            handler.waitFor();
        } catch (IOException e) {
            throw failedToStart(commandLine, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DreaminaCliException("Interrupted while awaiting Dreamina CLI subprocess", e, null);
        }

        return completeAfterWait(commandLine, timeoutMs, out, err, handler, watchdog, null);
    }

    /**
     * 创建 {@link #run(CommandLine)} 使用的进程执行器（包内可见，供单测注入抛出 {@link IOException} 的子类）。
     */
    DefaultExecutor newRunExecutor() {
        return new DefaultExecutor();
    }

    /**
     * 子进程已结束后，解析输出并映射为 {@link DreaminaCliResult} 或抛出执行层异常（包内可见，供单测注入 handler）。
     *
     * @param asyncFailureOverride 仅测试注入：非 null 时覆盖 {@link DefaultExecuteResultHandler#getException()} 结果
     */
    DreaminaCliResult completeAfterWait(
        CommandLine commandLine,
        long timeoutMs,
        ByteArrayOutputStream out,
        ByteArrayOutputStream err,
        DefaultExecuteResultHandler handler,
        ExecuteWatchdog watchdog,
        Exception asyncFailureOverride) {
        String stdoutStr = new String(out.toByteArray(), StandardCharsets.UTF_8);
        String stderrStr = new String(err.toByteArray(), StandardCharsets.UTF_8);
        DreaminaParsedFields parsed = DreaminaCliOutputParser.parseBestEffort(stdoutStr, stderrStr);

        // --- 超时：Watchdog 结束进程，优先抛出超时异常 ---
        if (watchdog.killedProcess()) {
            DreaminaCliResult partial = snapshot(stdoutStr, stderrStr, readExitQuietly(handler), parsed);
            throw new DreaminaCliTimeoutException(
                "Dreamina CLI timed out after " + timeoutMs + " ms: " + commandLine, partial);
        }

        // --- ExecuteException：通常对应非零退出或进程被破坏 ---
        Exception asyncFailure = asyncFailureOverride != null ? asyncFailureOverride : handler.getException();
        if (asyncFailure instanceof ExecuteException) {
            ExecuteException ex = (ExecuteException) asyncFailure;
            DreaminaCliResult failed = snapshot(stdoutStr, stderrStr, normalizeExitValue(ex.getExitValue()), parsed);
            throw new DreaminaCliNonZeroExitException(
                "Dreamina CLI failed (exitCode=" + ex.getExitValue() + "): " + commandLine, failed);
        }
        if (asyncFailure != null) {
            DreaminaCliResult partial = snapshot(stdoutStr, stderrStr, readExitQuietly(handler), parsed);
            throw failedAsync(commandLine, asyncFailure, partial);
        }

        final int exit;
        try {
            exit = handler.getExitValue();
        } catch (IllegalStateException e) {
            throw missingExitCode(commandLine, e, snapshot(stdoutStr, stderrStr, null, parsed));
        }

        if (exit != 0) {
            DreaminaCliResult failed = snapshot(stdoutStr, stderrStr, exit, parsed);
            throw nonZeroExitWithoutExecuteException(commandLine, exit, failed);
        }

        return DreaminaCliResult.builder()
            .stdout(stdoutStr)
            .stderr(stderrStr)
            .exitCode(exit)
            .success(true)
            .parsed(parsed)
            .build();
    }

    /**
     * 静默读取异步 handler 退出码。
     */
    private static Integer readExitQuietly(DefaultExecuteResultHandler handler) {
        try {
            int v = handler.getExitValue();
            return normalizeExitValue(v);
        } catch (IllegalStateException e) {
            return null;
        }
    }

    /**
     * 将 Commons Exec 的「未定义」哨兵值规整为 {@code null}。
     */
    private static Integer normalizeExitValue(int raw) {
        if (raw == org.apache.commons.exec.Executor.INVALID_EXITVALUE) {
            return null;
        }
        return raw;
    }

    private static DreaminaCliResult snapshot(
        String stdoutStr, String stderrStr, Integer exitCode, DreaminaParsedFields parsed) {
        return DreaminaCliResult.builder()
            .stdout(stdoutStr == null ? "" : stdoutStr)
            .stderr(stderrStr == null ? "" : stderrStr)
            .exitCode(exitCode)
            .success(false)
            .parsed(parsed)
            .build();
    }

    /**
     * 子进程无法启动时的统一异常（包内可见，供单测覆盖 spawn 失败分支）。
     */
    static DreaminaCliExecutableFailureException failedToStart(CommandLine commandLine, IOException cause) {
        log.warn("Dreamina CLI spawn failed commandLine={}, message={}", commandLine, cause.getMessage());
        return new DreaminaCliExecutableFailureException(
            "Dreamina CLI could not be started (check PATH or executable path): " + commandLine, cause);
    }

    /**
     * 非 {@link ExecuteException} 的异步失败（包内可见，供单测覆盖）。
     */
    static DreaminaCliException failedAsync(
        CommandLine commandLine, Exception asyncFailure, DreaminaCliResult partial) {
        return new DreaminaCliException(
            "Dreamina CLI async failure: " + commandLine + " cause=" + asyncFailure.getMessage(),
            asyncFailure,
            partial);
    }

    /**
     * 进程结束但无法读取退出码（包内可见，供单测覆盖）。
     */
    static DreaminaCliException missingExitCode(
        CommandLine commandLine, IllegalStateException cause, DreaminaCliResult partial) {
        return new DreaminaCliException(
            "Dreamina CLI completed without observable exit code: " + commandLine, cause, partial);
    }

    /**
     * 非零退出且未包装为 {@link ExecuteException} 的场景（包内可见，供单测覆盖）。
     */
    static DreaminaCliNonZeroExitException nonZeroExitWithoutExecuteException(
        CommandLine commandLine, int exitCode, DreaminaCliResult failed) {
        return new DreaminaCliNonZeroExitException(
            "Dreamina CLI non-zero exit (exitCode=" + exitCode + "): " + commandLine, failed);
    }
}
