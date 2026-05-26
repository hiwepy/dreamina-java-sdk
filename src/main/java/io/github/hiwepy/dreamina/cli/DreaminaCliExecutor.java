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
import io.github.hiwepy.dreamina.cli.DreaminaCliResponse;
import io.github.hiwepy.dreamina.cli.model.DreaminaCheckLogin;
import io.github.hiwepy.dreamina.cli.model.DreaminaDeviceLogin;
import io.github.hiwepy.dreamina.cli.model.DreaminaGenerateSubmit;
import io.github.hiwepy.dreamina.cli.model.DreaminaHelp;
import io.github.hiwepy.dreamina.cli.model.DreaminaLogin;
import io.github.hiwepy.dreamina.cli.model.DreaminaLogout;
import io.github.hiwepy.dreamina.cli.model.DreaminaQueryResult;
import io.github.hiwepy.dreamina.cli.model.DreaminaRelogin;
import io.github.hiwepy.dreamina.cli.model.DreaminaSessionDelete;
import io.github.hiwepy.dreamina.cli.model.DreaminaSessionList;
import io.github.hiwepy.dreamina.cli.model.DreaminaSessionMutation;
import io.github.hiwepy.dreamina.cli.model.DreaminaSessionSearch;
import io.github.hiwepy.dreamina.cli.model.DreaminaTaskItem;
import io.github.hiwepy.dreamina.cli.model.DreaminaUserCredit;
import io.github.hiwepy.dreamina.cli.model.DreaminaVersion;
import io.github.hiwepy.dreamina.cli.DreaminaCliResult;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import io.github.hiwepy.dreamina.cli.support.SubprocessExecutionSupport;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecuteResultHandler;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteWatchdog;

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
 *   <li><b>结构化视图</b>：{@code versionInfo}/{@code *Submit} 等系列便捷方法与 {@link DreaminaCliResponse}
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
@Getter
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
        SubprocessExecutionSupport.configureMaxConcurrentExecutions(properties.getMaxConcurrentExecutions());
    }

    // -------------------------------------------------------------------------
    // 帮助（help）
    // -------------------------------------------------------------------------

    /**
     * 调用 {@code dreamina help} 打印总帮助或等价输出。
     * <p>CLI 帮助（采集自本机 {@code dreamina help}）：</p>
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
     * <p>CLI 帮助（采集自本机 {@code dreamina version -h}）：</p>
     * <pre>
     * Usage:
     *   dreamina version [flags]
     * 
     * Print build version and commit information
     * 
     * 
     * Flags:
     *   -h, --help   help for version
     * 
     * Global Flags:
     *       --version   print build version information
     * 
     * Examples:
     *   dreamina version
     * </pre>
     */
    public DreaminaCliResult version() {
        return invoke(DreaminaCliSubcommands.Account.VERSION, Collections.emptyList());
    }

    /**
     * 调用 {@code dreamina user_credit} 查询与用户额度相关的 CLI 原始输出。
     * <p>CLI 帮助（采集自本机 {@code dreamina user_credit -h}）：</p>
     * <pre>
     * Usage:
     *   dreamina user_credit [flags]
     * 
     * Query the current logged-in user's remaining Dreamina credits.
     * 
     * 
     * Flags:
     *   -h, --help   help for user_credit
     * 
     * Global Flags:
     *       --version   print build version information
     * 
     * Examples:
     *   dreamina user_credit
     * </pre>
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
     * <p>CLI 帮助（采集自本机 {@code dreamina login -h}）：</p>
     * <pre>
     * Usage:
     *   dreamina login [flags]
     * 
     * Reuse the current local OAuth login state when it is still valid; otherwise start OAuth Device Flow.
     * By default the CLI prints verification_uri, user_code, and device_code, then waits for authorization to complete.
     * With --headless, the CLI prints the authorization material and exits without polling checklogin.
     * The legacy browser callback and manual-import login flow are no longer used.
     * 
     * 
     * Flags:
     *       --headless   print OAuth authorization material and exit without polling checklogin
     *   -h, --help       help for login
     * 
     * Global Flags:
     *       --version   print build version information
     * 
     * Examples:
     *   dreamina login
     *   dreamina login --headless
     *   dreamina login checklogin --device_code=<device_code> --poll=30
     * </pre>
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
     * <p>CLI 帮助（采集自本机 {@code dreamina login checklogin -h}）：</p>
     * <pre>
     * Usage:
     *   dreamina login checklogin [flags]
     * 
     * Check the authorization result for a prior headless OAuth Device Flow login.
     * Pass the device_code printed by "dreamina login --headless" or "dreamina relogin --headless".
     * --poll=N waits for up to N seconds; --poll=0 checks only once.
     * 
     * 
     * Flags:
     *       --device_code string   device code printed by a prior headless OAuth login
     *   -h, --help                 help for checklogin
     *       --poll int             wait for up to N seconds before timing out; 0 checks once
     * 
     * Global Flags:
     *       --version   print build version information
     * 
     * Examples:
     *   dreamina login checklogin --device_code=<device_code>
     *   dreamina login checklogin --device_code=<device_code> --poll=30
     * </pre>
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
     * <p>CLI 帮助（采集自本机 {@code dreamina logout -h}）：</p>
     * <pre>
     * Usage:
     *   dreamina logout [flags]
     * 
     * Remove the local OAuth login state without touching tasks or config.
     * 
     * 
     * Flags:
     *   -h, --help   help for logout
     * 
     * Global Flags:
     *       --version   print build version information
     * 
     * Examples:
     *   dreamina logout
     * </pre>
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
     * <p>CLI 帮助（采集自本机 {@code dreamina relogin -h}）：</p>
     * <pre>
     * Usage:
     *   dreamina relogin [flags]
     * 
     * Remove the local OAuth login state first, then force a fresh OAuth Device Flow login.
     * By default the CLI prints verification_uri, user_code, and device_code, then waits for authorization to complete.
     * With --headless, the CLI prints the authorization material and exits without polling checklogin.
     * 
     * 
     * Flags:
     *       --headless   print OAuth authorization material and exit without polling checklogin
     *   -h, --help       help for relogin
     * 
     * Global Flags:
     *       --version   print build version information
     * 
     * Examples:
     *   dreamina relogin
     *   dreamina relogin --headless
     * </pre>
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
     * <p>CLI 帮助（采集自本机 {@code dreamina session -h}）：</p>
     * <pre>
     * Usage:
     *   dreamina session [flags]
     * 
     * Manage Dreamina sessions (create, list, search, rename, delete).
     * 
     * A session is a container for organizing your creation history.
     * All generator commands accept a --session=<id> flag to submit tasks into a specific session.
     * 
     * Available Commands:
     *   create    Create a new session (auto-named or custom)
     *   list      List your recent sessions (alias: ls)
     *   search    Find a session ID by its name (alias: find)
     *   rename    Change a session's name (alias: update)
     *   delete    Delete a session (alias: rm)
     * 
     * Notes:
     * - All session commands require login (run "dreamina login" first).
     * - Session 0 is the default session. It cannot be renamed or deleted.
     * - Deleting a session will safely move its history back to the default session.
     * 
     * 
     * Flags:
     *   -h, --help   help for session
     * 
     * Global Flags:
     *       --version   print build version information
     * 
     * Examples:
     *   # 1. Create a session
     *   dreamina session create
     *   dreamina session create "My Video Project"
     * 
     *   # 2. List sessions (default 30; user-specified -n is capped at 100)
     *   dreamina session list
     *   dreamina session ls -n 100
     * 
     *   # 3. Find a session by name
     *   dreamina session search "Video"
     * 
     *   # 4. Rename a session
     *   dreamina session rename 10086 "New Project Name"
     * 
     *   # 5. Delete a session
     *   dreamina session rm 10086
     * </pre>
     */
    public DreaminaCliResult session(List<String> additionalRawArgs) {
        return invoke(DreaminaCliSubcommands.Account.SESSION, additionalRawArgs);
    }

    /**
     * {@link #session()} 的结构化视图：无子命令时 CLI 打印 session 子命令帮助（纯文本，见 {@link DreaminaCliResponse#getCombinedText()}）。
     */
    public DreaminaCliResponse<DreaminaHelp> sessionInfo() {
        return structuredPayloadMapper.mapHelp(DreaminaCliSubcommands.Account.SESSION, session());
    }

    /**
     * {@link #session(List)} 的结构化视图（如 {@code dreamina session -h}）。
     *
     * @param additionalRawArgs 透传到 CLI 的 flag
     */
    public DreaminaCliResponse<DreaminaHelp> sessionInfo(List<String> additionalRawArgs) {
        return structuredPayloadMapper.mapHelp(DreaminaCliSubcommands.Account.SESSION, session(additionalRawArgs));
    }

    /**
     * {@code dreamina session create}；无额外参数（等价于 {@link #sessionCreate(List)} 空列表）。
     */
    public DreaminaCliResult sessionCreate() {
        return sessionCreate(Collections.emptyList());
    }

    /**
     * {@code dreamina session create}；创建参数（名称、模型等）以官方 CLI 为准，通过 {@code additionalRawArgs} 传入。
     * <p>CLI 帮助（采集自本机 {@code dreamina session create -h}）：</p>
     * <pre>
     * Usage:
     *   dreamina session create [name] [flags]
     * 
     * Create a new session.
     * 
     * Args:
     * - name (optional): session name. If omitted, the backend generates a default name like "新对话 01-04 10:30".
     * 
     * Notes:
     * - name must be 1-50 characters after trimming spaces.
     * 
     * 
     * 
     * Flags:
     *   -h, --help   help for create
     * 
     * Global Flags:
     *       --version   print build version information
     * 
     * Examples:
     *   dreamina session create
     *   dreamina session create "我的视频项目"
     * </pre>
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
     * <p>CLI 帮助（采集自本机 {@code dreamina session list -h}）：</p>
     * <pre>
     * Usage:
     *   dreamina session list [flags]
     * 
     * List recent sessions.
     * 
     * By default it requests and shows the latest 30 sessions from the backend, ordered by pinned first and then updated time descending.
     * If you pass -n/--max-count, the CLI requests that many sessions from the backend.
     * User-specified values are capped at 100.
     * 
     * Output:
     * - Table columns: ID, NAME, PINNED, UPDATED_AT
     * - UPDATED_AT is formatted as local time: YYYY-MM-DD HH:MM
     * 
     * 
     * 
     * Flags:
     *   -h, --help            help for list
     *   -n, --max-count int   maximum number of sessions to display (default 30)
     * 
     * Global Flags:
     *       --version   print build version information
     * 
     * Examples:
     *   dreamina session list
     *   dreamina session list -n 5
     *   dreamina session list -n 100
     * </pre>
     */
    public DreaminaCliResult sessionList(List<String> additionalRawArgs) {
        return runSessionSub(DreaminaCliSubcommands.SessionSub.LIST, null, null, additionalRawArgs);
    }

    /**
     * {@code dreamina session ls}：{@code session list} 的官方别名；常用 {@code -n/--max-count} 可通过
     * {@code additionalRawArgs} 传入。
     *
     * @param additionalRawArgs 可选 flag；可为 null
     */
    public DreaminaCliResult sessionLs(List<String> additionalRawArgs) {
        return runSessionSub(DreaminaCliSubcommands.SessionSub.LS, null, null, additionalRawArgs);
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
     * <p>CLI 帮助（采集自本机 {@code dreamina session search -h}）：</p>
     * <pre>
     * Usage:
     *   dreamina session search <name> [flags]
     * 
     * Search sessions by name.
     * 
     * The CLI requests the first 100 sessions from the backend and matches records whose name contains the input string. Matching is case-sensitive.
     * 
     * 
     * 
     * Flags:
     *   -h, --help   help for search
     * 
     * Global Flags:
     *       --version   print build version information
     * 
     * Examples:
     *   dreamina session search "视频"
     *   dreamina session search "我的年度总结"
     * </pre>
     */
    public DreaminaCliResult sessionSearch(String searchTerm, List<String> additionalRawArgs) {
        return runSessionSub(DreaminaCliSubcommands.SessionSub.SEARCH, searchTerm, null, additionalRawArgs);
    }

    /**
     * {@code dreamina session find <searchTerm>}：{@code session search} 的官方别名。
     *
     * @param searchTerm        检索词；可为 null
     * @param additionalRawArgs 其它 flag；可为 null
     */
    public DreaminaCliResult sessionFind(String searchTerm, List<String> additionalRawArgs) {
        return runSessionSub(DreaminaCliSubcommands.SessionSub.FIND, searchTerm, null, additionalRawArgs);
    }

    /**
     * {@code dreamina session rename <sessionId> <newName>}。
     */
    public DreaminaCliResult sessionRename(String sessionId, String newName) {
        return sessionRename(sessionId, newName, Collections.emptyList());
    }

    /**
     * {@code dreamina session rename <sessionId> <newName>}（后两项为独立 argv，含空格时由 Commons Exec 处理转义）。
     * <p>CLI 帮助（采集自本机 {@code dreamina session rename -h}）：</p>
     * <pre>
     * Usage:
     *   dreamina session rename <session_id> <new_name> [flags]
     * 
     * Rename a session.
     * 
     * This command only exposes renaming. Pin/unpin is intentionally not exposed in CLI.
     * 
     * Args:
     * - session_id: the target session ID
     * - new_name: the new session name (1-50 characters)
     * 
     * Notes:
     * - Session 0 is the default session and cannot be renamed.
     * - Negative session IDs are invalid.
     * 
     * 
     * 
     * Flags:
     *   -h, --help   help for rename
     * 
     * Global Flags:
     *       --version   print build version information
     * 
     * Examples:
     *   dreamina session rename 10086 "2024年度宣传片"
     * </pre>
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
     * {@code dreamina session update <sessionId> <newName>}：{@code session rename} 的官方别名。
     */
    public DreaminaCliResult sessionUpdate(String sessionId, String newName) {
        return sessionUpdate(sessionId, newName, Collections.emptyList());
    }

    /**
     * {@code dreamina session update <sessionId> <newName>}。
     *
     * @param sessionId         当前会话标识；不得为 null/空白
     * @param newName           新显示名；不得为 null/空白
     * @param additionalRawArgs 其它 flag；可为 null
     */
    public DreaminaCliResult sessionUpdate(String sessionId, String newName, List<String> additionalRawArgs) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(newName, "newName");
        if (DreaminaStrings.isBlank(sessionId) || DreaminaStrings.isBlank(newName)) {
            throw new IllegalArgumentException("sessionId and newName must not be blank");
        }
        return runSessionSub(
            DreaminaCliSubcommands.SessionSub.UPDATE, sessionId.trim(), newName.trim(), additionalRawArgs);
    }

    /**
     * {@code dreamina session delete <sessionId>}。
     * <p>CLI 帮助（采集自本机 {@code dreamina session delete -h}）：</p>
     * <pre>
     * Usage:
     *   dreamina session delete <session_id> [flags]
     * 
     * Delete a session.
     * 
     * Notes:
     * - Session 0 is the default session and cannot be deleted.
     * - Negative session IDs are invalid.
     * - This operation is safe. The backend performs a soft delete and will move related history records back to the default session.
     * 
     * 
     * 
     * Flags:
     *   -h, --help   help for delete
     * 
     * Global Flags:
     *       --version   print build version information
     * 
     * Examples:
     *   dreamina session delete 10085
     *   dreamina session rm 10085
     * </pre>
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
     * {@code dreamina session rm <sessionId>}：{@code session delete} 的官方别名。
     */
    public DreaminaCliResult sessionRm(String sessionId) {
        return sessionRm(sessionId, Collections.emptyList());
    }

    /**
     * {@code dreamina session rm <sessionId>}。
     *
     * @param sessionId         要删除的会话标识；不得为 null/空白
     * @param additionalRawArgs 其它 flag；可为 null
     */
    public DreaminaCliResult sessionRm(String sessionId, List<String> additionalRawArgs) {
        Objects.requireNonNull(sessionId, "sessionId");
        if (DreaminaStrings.isBlank(sessionId)) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        return runSessionSub(DreaminaCliSubcommands.SessionSub.RM, sessionId.trim(), null, additionalRawArgs);
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
     * <p>CLI 帮助（采集自本机 {@code dreamina text2image -h}）：</p>
     * <pre>
     * Usage:
     *   dreamina text2image [flags]
     * 
     * Submit a Dreamina text-to-image task. The task is asynchronous, but --poll can wait briefly before falling back to query_result.
     * 
     * Supported combinations:
     * - model_version: 3.0, 3.1, 4.0, 4.1, 4.5, 4.6, 5.0
     * - ratio: 21:9, 16:9, 3:2, 4:3, 1:1, 3:4, 2:3, 9:16
     * - 3.0/3.1 -> resolution_type 1k or 2k
     * - 4.0/4.1/4.5/4.6/5.0 -> resolution_type 2k or 4k
     * 
     * Notes:
     * - omit --model_version to use the default model
     * - omit --resolution_type to use the model default
     * 
     * 
     * Flags:
     *       --prompt string            generation prompt
     *       --session int              session id (default 0 "默认对话") 
     *       --ratio string             supported values: 21:9, 16:9, 3:2, 4:3, 1:1, 3:4, 2:3, 9:16
     *       --resolution_type string   supported values by model: 3.0/3.1 -> 1k or 2k; 4.0/4.1/4.5/4.6/5.0 -> 2k or 4k; omit to use the model default
     *       --model_version string     supported values: 3.0, 3.1, 4.0, 4.1, 4.5, 4.6, 5.0
     *       --poll int                 submit then poll query_result for up to N seconds at 1s intervals (0 disables polling)
     *   -h, --help                     help for text2image
     * 
     * Global Flags:
     *       --version   print build version information
     * 
     * Examples:
     *   dreamina text2image --prompt="a cat portrait" --ratio=1:1 --resolution_type=2k
     * </pre>
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
     * <p>CLI 帮助（采集自本机 {@code dreamina image2image -h}）：</p>
     * <pre>
     * Usage:
     *   dreamina image2image [flags]
     * 
     * Upload 1 to 10 local images, then submit a Dreamina image-to-image task. The task is asynchronous, but --poll can wait briefly before falling back to query_result.
     * 
     * Supported combinations:
     * - model_version: 4.0, 4.1, 4.5, 4.6, 5.0
     * - ratio: 21:9, 16:9, 3:2, 4:3, 1:1, 3:4, 2:3, 9:16
     * - resolution_type: 2k, 4k
     * 
     * Notes:
     * - 1k is not supported for image2image
     * - omit --model_version to use the default model
     * - omit --resolution_type to use the model default
     * - 一次最多上传十张图片，否则可能导致生图失败
     * 
     * 
     * Flags:
     *       --images strings           local input image paths
     *       --prompt string            edit prompt
     *       --session int              session id (default 0 "默认对话") 
     *       --ratio string             supported values: 21:9, 16:9, 3:2, 4:3, 1:1, 3:4, 2:3, 9:16
     *       --resolution_type string   supported values: 2k, 4k; omit to use the model default
     *       --model_version string     supported values: 4.0, 4.1, 4.5, 4.6, 5.0
     *       --poll int                 submit then poll query_result for up to N seconds at 1s intervals (0 disables polling)
     *   -h, --help                     help for image2image
     * 
     * Global Flags:
     *       --version   print build version information
     * 
     * Examples:
     *   dreamina image2image --images ./input.png --prompt="turn into watercolor"
     * </pre>
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
     * <p>CLI 帮助（采集自本机 {@code dreamina image_upscale -h}）：</p>
     * <pre>
     * Usage:
     *   dreamina image_upscale [flags]
     * 
     * Upload one local image, then submit a Dreamina image upscale task. The task is asynchronous, but --poll can wait briefly before falling back to query_result.
     * 
     * Supported combinations:
     * - resolution_type: 2k, 4k, 8k
     * - 2k is available to non-VIP users
     * - 4k and 8k require VIP
     * 
     * 
     * Flags:
     *       --image string             local input image path
     *       --session int              session id (default 0 "默认对话") 
     *       --resolution_type string   supported values: 2k, 4k, 8k; 4k and 8k require VIP
     *       --poll int                 submit then poll query_result for up to N seconds at 1s intervals (0 disables polling)
     *   -h, --help                     help for image_upscale
     * 
     * Global Flags:
     *       --version   print build version information
     * 
     * Examples:
     *   dreamina image_upscale --image=./input.png --resolution_type=4k
     * </pre>
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
     * <p>CLI 帮助（采集自本机 {@code dreamina text2video -h}）：</p>
     * <pre>
     * Usage:
     *   dreamina text2video [flags]
     * 
     * Submit a Dreamina text-to-video task. The task is asynchronous, but --poll can wait briefly before falling back to query_result.
     * 
     * Supported combinations:
     * - model_version: seedance2.0, seedance2.0fast, seedance2.0_vip, seedance2.0fast_vip
     * - ratio: 1:1, 3:4, 16:9, 4:3, 9:16, 21:9
     * - seedance2.0_vip -> video_resolution 720p or 1080p; duration 4-15s
     * - all other models -> video_resolution 720p; duration 4-15s
     * 
     * Notes:
     * - default model_version: seedance2.0fast
     * - omit --video_resolution to use the model default
     * - omit --ratio to use the default ratio
     * - 部分高内容安全风险模型在首次使用前，可能需要先在 Dreamina Web 端完成授权确认。若返回 AigcComplianceConfirmationRequired，请先完成授权后重试。
     * 
     * 
     * Flags:
     *       --prompt string             generation prompt
     *       --session int               session id (default 0 "默认对话") 
     *       --duration int              video duration in seconds; supported range: 4-15 (default 5)
     *       --ratio string              supported values: 1:1, 3:4, 16:9, 4:3, 9:16, 21:9
     *       --video_resolution string   supported values by model: seedance2.0_vip -> 720p or 1080p; all other models -> 720p
     *       --model_version string      supported values: seedance2.0, seedance2.0fast, seedance2.0_vip, seedance2.0fast_vip; default: seedance2.0fast
     *       --poll int                  submit then poll query_result for up to N seconds at 1s intervals (0 disables polling)
     *   -h, --help                      help for text2video
     * 
     * Global Flags:
     *       --version   print build version information
     * 
     * Examples:
     *   dreamina text2video --prompt="a cat running" --duration=5
     * </pre>
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
     * <p>CLI 帮助（采集自本机 {@code dreamina image2video -h}）：</p>
     * <pre>
     * Usage:
     *   dreamina image2video [flags]
     * 
     * Upload one local image, then submit a Dreamina image-to-video task. For multi-image storytelling, use multiframe2video; for full-reference mixed-media generation, use multimodal2video. The task is asynchronous, but --poll can wait briefly before falling back to query_result.
     * 
     * Supported combinations:
     * - basic usage: --image + --prompt
     * - advanced controls: set any of --duration, --video_resolution, or --model_version
     * - advanced model_version values: 3.0, 3.0fast, 3.0pro, 3.0_fast, 3.0_pro, 3.5pro, 3.5_pro, seedance2.0, seedance2.0fast, seedance2.0_vip, seedance2.0fast_vip
     * - seedance2.0_vip -> video_resolution 720p or 1080p
     * - all other models -> video_resolution 720p
     * - ratio is inferred from the input image and is not set on this command
     * 
     * Notes:
     * - omit advanced controls to use the default image-to-video path
     * - duration, model_version, and video_resolution must be provided in a supported combination
     * - 部分高内容安全风险模型在首次使用前，可能需要先在 Dreamina Web 端完成授权确认。若返回 AigcComplianceConfirmationRequired，请先完成授权后重试。
     * 
     * 
     * Flags:
     *       --image string              local first-frame image path
     *       --prompt string             generation prompt
     *       --duration int              advanced controls only; supported duration ranges by model: 3.0/3.0fast/3.0pro -> 3-10, 3.5pro -> 4-12, seedance2.0 family -> 4-15 (default 5)
     *       --video_resolution string   advanced controls only; supported values by model: seedance2.0_vip -> 720p or 1080p; all other models -> 720p
     *       --model_version string      advanced controls only; supported values: 3.0, 3.0fast, 3.0pro, 3.0_fast, 3.0_pro, 3.5pro, 3.5_pro, seedance2.0, seedance2.0fast, seedance2.0_vip, seedance2.0fast_vip
     *       --session int               session id (default 0 "默认对话") 
     *       --poll int                  submit then poll query_result for up to N seconds at 1s intervals (0 disables polling)
     *   -h, --help                      help for image2video
     * 
     * Global Flags:
     *       --version   print build version information
     * 
     * Examples:
     *   dreamina image2video --image=./first.png --prompt="camera push in"
     * </pre>
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
     * <p>CLI 帮助（采集自本机 {@code dreamina frames2video -h}）：</p>
     * <pre>
     * Usage:
     *   dreamina frames2video [flags]
     * 
     * Upload two local images as first and last frames, then submit a Dreamina video generation task. The task is asynchronous, but --poll can wait briefly before falling back to query_result.
     * 
     * Supported combinations:
     * - model_version: 3.0, 3.5pro, seedance2.0, seedance2.0fast, seedance2.0_vip, seedance2.0fast_vip
     * - seedance2.0_vip -> video_resolution 720p or 1080p; duration 4-15s
     * - 3.0 -> video_resolution 720p; duration 3-10s
     * - 3.5pro -> video_resolution 720p; duration 4-12s
     * - all other seedance2.0 models -> video_resolution 720p; duration 4-15s
     * 
     * Notes:
     * - ratio is inferred from the first frame image size
     * - default model_version: seedance2.0fast
     * - omit --video_resolution to use the model default
     * - 部分高内容安全风险模型在首次使用前，可能需要先在 Dreamina Web 端完成授权确认。若返回 AigcComplianceConfirmationRequired，请先完成授权后重试。
     * 
     * 
     * Flags:
     *       --first string              local first-frame image path
     *       --last string               local last-frame image path
     *       --prompt string             generation prompt
     *       --session int               session id (default 0 "默认对话") 
     *       --duration int              video duration in seconds; supported ranges: 3.0 -> 3-10, 3.5pro -> 4-12, seedance2.0 family -> 4-15 (default 5)
     *       --video_resolution string   supported values by model: seedance2.0_vip -> 720p or 1080p; all other models -> 720p
     *       --model_version string      supported values: 3.0, 3.5pro, seedance2.0, seedance2.0fast, seedance2.0_vip, seedance2.0fast_vip; default: seedance2.0fast
     *       --poll int                  submit then poll query_result for up to N seconds at 1s intervals (0 disables polling)
     *   -h, --help                      help for frames2video
     * 
     * Global Flags:
     *       --version   print build version information
     * 
     * Examples:
     *   dreamina frames2video --first=./start.png --last=./end.png --prompt="season changes"
     * </pre>
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
     * <p>CLI 帮助（采集自本机 {@code dreamina multiframe2video -h}）：</p>
     * <pre>
     * Usage:
     *   dreamina multiframe2video [flags]
     * 
     * Upload multiple local images, then submit a Dreamina intelligent multi-frame video task for coherent visual storytelling. The task is asynchronous, but --poll can wait briefly before falling back to query_result.
     * 
     * Supported combinations:
     * - inputs: 2-20 images
     * - exactly 2 images: use shorthand --prompt and optional --duration
     * - 3+ images: repeat --transition-prompt once per transition segment to describe how one frame evolves into the next
     * - repeat --transition-duration once per transition segment, or omit it to default each segment to 3 seconds
     * 
     * Notes:
     * - designed for multi-image story generation, not full multimodal editing
     * - for N images, the transition count is N-1
     * - ratio is inferred from the first image
     * - model_version and video_resolution overrides are not supported by this command
     * - each duration segment is limited to [0.5, 8] seconds and total duration must be >= 2
     * 
     * 
     * Flags:
     *       --images strings                    local reference image paths
     *       --prompt string                     shorthand prompt for exactly 2 images
     *       --duration float                    shorthand transition duration in seconds for exactly 2 images; backend clamps each segment to [0.5, 8] and requires total duration >= 2 (default 3)
     *       --transition-prompt stringArray     repeat once per transition segment; for N images provide N-1 prompts
     *       --transition-duration stringArray   repeat once per transition segment in seconds; for N images provide N-1 durations, or omit to default each segment to 3
     *       --session int                       session id (default 0 "默认对话") 
     *       --poll int                          submit then poll query_result for up to N seconds at 1s intervals (0 disables polling)
     *   -h, --help                              help for multiframe2video
     * 
     * Global Flags:
     *       --version   print build version information
     * 
     * Examples:
     *   dreamina multiframe2video --images ./a.png,./b.png --prompt="character turns around"
     *   dreamina multiframe2video --images ./a.png,./b.png,./c.png --transition-prompt="turn from A to B" --transition-prompt="turn from B to C"
     * </pre>
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
     * <p>CLI 帮助（采集自本机 {@code dreamina multimodal2video -h}）：</p>
     * <pre>
     * Usage:
     *   dreamina multimodal2video [flags]
     * 
     * Upload local images, videos, and audio, then submit Dreamina's flagship multimodal video generation mode. This corresponds to the "全能参考" (All-around reference) feature on the web interface (formerly known as ref2video). This is the strongest video generation mode currently exposed in the CLI, supports all-around references, and supports the Seedance 2.0 family (flag values: seedance2.0, seedance2.0fast, seedance2.0_vip, seedance2.0fast_vip). The task is asynchronous, but --poll can wait briefly before falling back to query_result.
     * 
     * Supported combinations:
     * - inputs: any mix of --image, --video, --audio
     * - at least one --image or --video is required
     * - audio inputs must be 2-15 seconds
     * - model_version: seedance2.0, seedance2.0fast, seedance2.0_vip, seedance2.0fast_vip
     * - ratio: 1:1, 3:4, 16:9, 4:3, 9:16, 21:9
     * - seedance2.0_vip -> video_resolution 720p or 1080p
     * - all other models -> video_resolution 720p
     * - duration: 4-15s
     * 
     * Notes:
     * - local files are uploaded automatically before submit
     * - input limits: image<=9, video<=3, audio<=3
     * - 部分高内容安全风险模型在首次使用前，可能需要先在 Dreamina Web 端完成授权确认。若返回 AigcComplianceConfirmationRequired，请先完成授权后重试。
     * 
     * 
     * Flags:
     *       --image stringArray         repeat for each local input image path
     *       --video stringArray         repeat for each local input video path
     *       --audio stringArray         repeat for each local input audio path
     *       --prompt string             optional multimodal edit prompt
     *       --duration int              video duration in seconds; supported range: 4-15 (default 5)
     *       --ratio string              supported values: 1:1, 3:4, 16:9, 4:3, 9:16, 21:9
     *       --video_resolution string   supported values by model: seedance2.0_vip -> 720p or 1080p; all other models -> 720p
     *       --model_version string      supported values: seedance2.0, seedance2.0fast, seedance2.0_vip, seedance2.0fast_vip
     *       --session int               session id (default 0 "默认对话") 
     *       --poll int                  submit then poll query_result for up to N seconds at 1s intervals (0 disables polling)
     *   -h, --help                      help for multimodal2video
     * 
     * Global Flags:
     *       --version   print build version information
     * 
     * Examples:
     *   dreamina multimodal2video --image ./input.png --prompt="turn this into a cinematic shot"
     *   dreamina multimodal2video --image ./input.png --audio ./music.mp3 --model_version=seedance2.0fast --duration=5
     *   dreamina multimodal2video --image ./input.png --video ./ref.mp4 --audio ./music.mp3 --model_version=seedance2.0fast --duration=5
     * </pre>
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
     * <p>CLI 帮助（采集自本机 {@code dreamina query_result -h}）：</p>
     * <pre>
     * Usage:
     *   dreamina query_result [flags]
     * 
     * Query one async task by submit_id.
     * 
     * 
     * Flags:
     *       --download_dir string   download result media into the target directory
     *   -h, --help                  help for query_result
     *       --submit_id string      task submit_id
     * 
     * Global Flags:
     *       --version   print build version information
     * 
     * Examples:
     *   dreamina query_result --submit_id=3f6eb41f425d23a3
     * </pre>
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
     * <p>CLI 帮助（采集自本机 {@code dreamina list_task -h}）：</p>
     * <pre>
     * Usage:
     *   dreamina list_task [flags]
     * 
     * List tasks saved for the current logged-in user.
     * 
     * 
     * Flags:
     *       --gen_status string      filter by gen_status
     *       --gen_task_type string   filter by gen_task_type
     *   -h, --help                   help for list_task
     *       --limit int              max number of tasks to return (default 20)
     *       --offset int             offset for pagination
     *       --submit_id string       filter by submit_id
     * 
     * Global Flags:
     *       --version   print build version information
     * 
     * Examples:
     *   dreamina list_task
     *   dreamina list_task --gen_status=success
     * </pre>
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
    // 结构化便捷封装（所见即所得：{@link DreaminaCliResponse}）
    // -------------------------------------------------------------------------

    /**
     * {@link #version()} 的结构化视图。
     *
     * @return 绑定原始快照与 {@link DreaminaVersion}
     */
    public DreaminaCliResponse<DreaminaVersion> versionInfo() {
        return structuredPayloadMapper.mapVersion(version());
    }

    /**
     * {@link #userCredit()} 的结构化视图。
     */
    public DreaminaCliResponse<DreaminaUserCredit> userCreditInfo() {
        return structuredPayloadMapper.mapUserCredit(userCredit());
    }

    /**
     * {@link #help()} 的结构化视图。
     */
    public DreaminaCliResponse<DreaminaHelp> helpInfo() {
        return structuredPayloadMapper.mapHelp(null, help());
    }

    /**
     * {@link #help(String)} 的结构化视图。
     *
     * @param subcommand 目标子命令名
     */
    public DreaminaCliResponse<DreaminaHelp> helpInfo(String subcommand) {
        return structuredPayloadMapper.mapHelp(subcommand, help(subcommand));
    }

    /**
     * {@link #help(String, List)} 的结构化视图。
     *
     * @param subcommand        目标子命令名
     * @param additionalRawArgs 追加原生参数
     */
    public DreaminaCliResponse<DreaminaHelp> helpInfo(String subcommand, List<String> additionalRawArgs) {
        return structuredPayloadMapper.mapHelp(subcommand, help(subcommand, additionalRawArgs));
    }

    /**
     * {@link #login()} 的结构化视图。
     */
    public DreaminaCliResponse<DreaminaLogin> loginInfo() {
        return structuredPayloadMapper.mapLogin(login());
    }

    /**
     * {@link #login(List)} 的结构化视图。
     */
    public DreaminaCliResponse<DreaminaLogin> loginInfo(List<String> additionalRawArgs) {
        return structuredPayloadMapper.mapLogin(login(additionalRawArgs));
    }

    /**
     * {@link #logout()} 的结构化视图。
     */
    public DreaminaCliResponse<DreaminaLogout> logoutInfo() {
        return structuredPayloadMapper.mapLogout(logout());
    }

    /**
     * {@link #logout(List)} 的结构化视图。
     */
    public DreaminaCliResponse<DreaminaLogout> logoutInfo(List<String> additionalRawArgs) {
        return structuredPayloadMapper.mapLogout(logout(additionalRawArgs));
    }

    /**
     * {@link #relogin()} 的结构化视图。
     */
    public DreaminaCliResponse<DreaminaRelogin> reloginInfo() {
        return structuredPayloadMapper.mapRelogin(relogin());
    }

    /**
     * {@link #relogin(List)} 的结构化视图。
     */
    public DreaminaCliResponse<DreaminaRelogin> reloginInfo(List<String> additionalRawArgs) {
        return structuredPayloadMapper.mapRelogin(relogin(additionalRawArgs));
    }

    /**
     * {@link #checkLogin(String, int)} 的结构化视图。
     */
    public DreaminaCliResponse<DreaminaCheckLogin> checkLoginInfo(String deviceCode, int pollSeconds) {
        return structuredPayloadMapper.mapCheckLogin(checkLogin(deviceCode, pollSeconds));
    }

    /**
     * {@link #checkLogin(String, int, List)} 的结构化视图。
     */
    public DreaminaCliResponse<DreaminaCheckLogin> checkLoginInfo(
        String deviceCode, int pollSeconds, List<String> additionalRawArgs) {
        return structuredPayloadMapper.mapCheckLogin(checkLogin(deviceCode, pollSeconds, additionalRawArgs));
    }

    /**
     * {@link #loginHeadless()} 的结构化视图：可能是 Device Flow JSON，也可能仅为「复用本地 OAuth」提示文本。
     */
    public DreaminaCliResponse<DreaminaLogin> loginHeadlessInfo() {
        return structuredPayloadMapper.mapLogin(loginHeadless());
    }

    /**
     * {@link #loginHeadless(List)} 的结构化视图。
     *
     * @param additionalRawArgs headless 后缀参数
     */
    public DreaminaCliResponse<DreaminaLogin> loginHeadlessInfo(List<String> additionalRawArgs) {
        return structuredPayloadMapper.mapLogin(loginHeadless(additionalRawArgs));
    }

    /**
     * 显式解析 Device Flow JSON（若 stdout 非 JSON 则字段为空）。
     */
    public DreaminaCliResponse<DreaminaDeviceLogin> deviceLoginMaterial(DreaminaCliResult loginStdOutSnapshot) {
        return structuredPayloadMapper.mapDeviceLogin(loginStdOutSnapshot);
    }

    /**
     * {@link #sessionList()} 的结构化视图。
     */
    public DreaminaCliResponse<DreaminaSessionList> sessionListInfo() {
        return structuredPayloadMapper.mapSessionList(sessionList());
    }

    /**
     * {@link #sessionList(List)} 的结构化视图。
     *
     * @param additionalRawArgs 透传到 CLI 的 flag
     */
    public DreaminaCliResponse<DreaminaSessionList> sessionListInfo(List<String> additionalRawArgs) {
        return structuredPayloadMapper.mapSessionList(sessionList(additionalRawArgs));
    }

    /**
     * {@link #sessionLs(List)} 的结构化视图。
     *
     * @param additionalRawArgs 透传到 CLI 的 flag，如 {@code -n=100}
     */
    public DreaminaCliResponse<DreaminaSessionList> sessionLsInfo(List<String> additionalRawArgs) {
        return structuredPayloadMapper.mapSessionList(sessionLs(additionalRawArgs));
    }

    /**
     * {@link #sessionSearch(String)} 的结构化视图。
     *
     * @param searchTerm 检索关键字；可为 null（等同底层 CLI 语义）
     */
    public DreaminaCliResponse<DreaminaSessionSearch> sessionSearchInfo(String searchTerm) {
        return structuredPayloadMapper.mapSessionSearch(searchTerm, sessionSearch(searchTerm));
    }

    /**
     * {@link #sessionSearch(String, List)} 的结构化视图。
     */
    public DreaminaCliResponse<DreaminaSessionSearch> sessionSearchInfo(
        String searchTerm, List<String> additionalRawArgs) {
        return structuredPayloadMapper.mapSessionSearch(searchTerm, sessionSearch(searchTerm, additionalRawArgs));
    }

    /**
     * {@link #sessionFind(String, List)} 的结构化视图。
     */
    public DreaminaCliResponse<DreaminaSessionSearch> sessionFindInfo(
        String searchTerm, List<String> additionalRawArgs) {
        return structuredPayloadMapper.mapSessionSearch(searchTerm, sessionFind(searchTerm, additionalRawArgs));
    }

    /**
     * {@link #sessionCreate()} 的结构化视图。
     */
    public DreaminaCliResponse<DreaminaSessionMutation> sessionCreateInfo() {
        return structuredPayloadMapper.mapSessionMutation(sessionCreate());
    }

    /**
     * {@link #sessionCreate(List)} 的结构化视图。
     *
     * @param additionalRawArgs 会话名称或其它官方 flag
     */
    public DreaminaCliResponse<DreaminaSessionMutation> sessionCreateInfo(List<String> additionalRawArgs) {
        return structuredPayloadMapper.mapSessionMutation(sessionCreate(additionalRawArgs));
    }

    /**
     * {@link #sessionRename(String, String)} 的结构化视图。
     */
    public DreaminaCliResponse<DreaminaSessionMutation> sessionRenameInfo(String sessionId, String newName) {
        return structuredPayloadMapper.mapSessionMutation(sessionRename(sessionId, newName));
    }

    /**
     * {@link #sessionRename(String, String, List)} 的结构化视图。
     */
    public DreaminaCliResponse<DreaminaSessionMutation> sessionRenameInfo(
        String sessionId, String newName, List<String> additionalRawArgs) {
        return structuredPayloadMapper.mapSessionMutation(sessionRename(sessionId, newName, additionalRawArgs));
    }

    /**
     * {@link #sessionUpdate(String, String, List)} 的结构化视图。
     */
    public DreaminaCliResponse<DreaminaSessionMutation> sessionUpdateInfo(
        String sessionId, String newName, List<String> additionalRawArgs) {
        return structuredPayloadMapper.mapSessionMutation(sessionUpdate(sessionId, newName, additionalRawArgs));
    }

    /**
     * {@link #listTask()} 的结构化视图。
     */
    public DreaminaCliResponse<List<DreaminaTaskItem>> listTaskInfo() {
        return structuredPayloadMapper.mapTaskList(listTask());
    }

    /**
     * {@link #listTask(List)} 的结构化视图。
     */
    public DreaminaCliResponse<List<DreaminaTaskItem>> listTaskInfo(List<String> additionalRawArgs) {
        return structuredPayloadMapper.mapTaskList(listTask(additionalRawArgs));
    }

    /**
     * {@link #listTask(DreaminaListTaskRequest)} 的结构化视图。
     */
    public DreaminaCliResponse<List<DreaminaTaskItem>> listTaskInfo(DreaminaListTaskRequest request) {
        return structuredPayloadMapper.mapTaskList(listTask(request));
    }

    /**
     * {@link #queryResult(String)} 的结构化视图。
     *
     * @param submitId 提交编号
     */
    public DreaminaCliResponse<DreaminaQueryResult> queryResultInfo(String submitId) {
        return structuredPayloadMapper.mapQueryResult(queryResult(submitId));
    }

    /**
     * {@link #queryResult(String, List)} 的结构化视图。
     */
    public DreaminaCliResponse<DreaminaQueryResult> queryResultInfo(String submitId, List<String> additionalRawArgs) {
        return structuredPayloadMapper.mapQueryResult(queryResult(submitId, additionalRawArgs));
    }

    /**
     * {@link #queryResult(DreaminaQueryResultRequest)} 的结构化视图。
     */
    public DreaminaCliResponse<DreaminaQueryResult> queryResultInfo(DreaminaQueryResultRequest request) {
        return structuredPayloadMapper.mapQueryResult(queryResult(request));
    }

    /**
     * {@link #text2Image(String, List)} 的结构化提交视图。
     */
    public DreaminaCliResponse<DreaminaGenerateSubmit> text2ImageSubmit(String prompt, List<String> additionalRawArgs) {
        return structuredPayloadMapper.mapGenerateSubmit(text2Image(prompt, additionalRawArgs));
    }

    /**
     * {@link #text2Image(DreaminaText2ImageRequest)} 的结构化提交视图。
     *
     * @param request 文生图请求
     * @return 原始快照与结构化提交结果
     */
    public DreaminaCliResponse<DreaminaGenerateSubmit> text2ImageSubmit(DreaminaText2ImageRequest request) {
        return structuredPayloadMapper.mapGenerateSubmit(text2Image(request));
    }

    /**
     * {@link #image2Image(String, String, List)} 的结构化提交视图。
     */
    public DreaminaCliResponse<DreaminaGenerateSubmit> image2ImageSubmit(
        String imagesCsv, String prompt, List<String> additionalRawArgs) {
        return structuredPayloadMapper.mapGenerateSubmit(image2Image(imagesCsv, prompt, additionalRawArgs));
    }

    /**
     * {@link #image2Image(DreaminaImage2ImageRequest)} 的结构化提交视图。
     *
     * @param request 图生图请求
     * @return 原始快照与结构化提交结果
     */
    public DreaminaCliResponse<DreaminaGenerateSubmit> image2ImageSubmit(DreaminaImage2ImageRequest request) {
        return structuredPayloadMapper.mapGenerateSubmit(image2Image(request));
    }

    /**
     * {@link #imageUpscale(List)} 的结构化提交视图。
     */
    public DreaminaCliResponse<DreaminaGenerateSubmit> imageUpscaleSubmit(List<String> additionalRawArgs) {
        return structuredPayloadMapper.mapGenerateSubmit(imageUpscale(additionalRawArgs));
    }

    /**
     * {@link #imageUpscale(DreaminaImageUpscaleRequest)} 的结构化提交视图。
     *
     * @param request 图像超分请求
     * @return 原始快照与结构化提交结果
     */
    public DreaminaCliResponse<DreaminaGenerateSubmit> imageUpscaleSubmit(DreaminaImageUpscaleRequest request) {
        return structuredPayloadMapper.mapGenerateSubmit(imageUpscale(request));
    }

    /**
     * {@link #text2video(String, List)} 的结构化提交视图。
     */
    public DreaminaCliResponse<DreaminaGenerateSubmit> text2VideoSubmit(String prompt, List<String> additionalRawArgs) {
        return structuredPayloadMapper.mapGenerateSubmit(text2video(prompt, additionalRawArgs));
    }

    /**
     * {@link #text2video(DreaminaText2VideoRequest)} 的结构化提交视图。
     *
     * @param request 文生视频请求
     * @return 原始快照与结构化提交结果
     */
    public DreaminaCliResponse<DreaminaGenerateSubmit> text2VideoSubmit(DreaminaText2VideoRequest request) {
        return structuredPayloadMapper.mapGenerateSubmit(text2video(request));
    }

    /**
     * {@link #image2video(String, String, List)} 的结构化提交视图。
     */
    public DreaminaCliResponse<DreaminaGenerateSubmit> image2VideoSubmit(
        String imagePath, String prompt, List<String> additionalRawArgs) {
        return structuredPayloadMapper.mapGenerateSubmit(image2video(imagePath, prompt, additionalRawArgs));
    }

    /**
     * {@link #image2video(DreaminaImage2VideoRequest)} 的结构化提交视图。
     *
     * @param request 单图生视频请求
     * @return 原始快照与结构化提交结果
     */
    public DreaminaCliResponse<DreaminaGenerateSubmit> image2VideoSubmit(DreaminaImage2VideoRequest request) {
        return structuredPayloadMapper.mapGenerateSubmit(image2video(request));
    }

    /**
     * {@link #frames2video(List)} 的结构化提交视图。
     */
    public DreaminaCliResponse<DreaminaGenerateSubmit> frames2VideoSubmit(List<String> additionalRawArgs) {
        return structuredPayloadMapper.mapGenerateSubmit(frames2video(additionalRawArgs));
    }

    /**
     * {@link #frames2video(DreaminaFrames2VideoRequest)} 的结构化提交视图。
     *
     * @param request 首尾帧视频请求
     * @return 原始快照与结构化提交结果
     */
    public DreaminaCliResponse<DreaminaGenerateSubmit> frames2VideoSubmit(DreaminaFrames2VideoRequest request) {
        return structuredPayloadMapper.mapGenerateSubmit(frames2video(request));
    }

    /**
     * {@link #multiframe2video(List)} 的结构化提交视图。
     */
    public DreaminaCliResponse<DreaminaGenerateSubmit> multiframe2VideoSubmit(List<String> additionalRawArgs) {
        return structuredPayloadMapper.mapGenerateSubmit(multiframe2video(additionalRawArgs));
    }

    /**
     * {@link #multiframe2video(DreaminaMultiframe2VideoRequest)} 的结构化提交视图。
     *
     * @param request 多帧故事视频请求
     * @return 原始快照与结构化提交结果
     */
    public DreaminaCliResponse<DreaminaGenerateSubmit> multiframe2VideoSubmit(
        DreaminaMultiframe2VideoRequest request) {
        return structuredPayloadMapper.mapGenerateSubmit(multiframe2video(request));
    }

    /**
     * {@link #multimodal2video(List)} 的结构化提交视图。
     */
    public DreaminaCliResponse<DreaminaGenerateSubmit> multimodal2VideoSubmit(List<String> additionalRawArgs) {
        return structuredPayloadMapper.mapGenerateSubmit(multimodal2video(additionalRawArgs));
    }

    /**
     * {@link #multimodal2video(DreaminaMultimodal2VideoRequest)} 的结构化提交视图。
     *
     * @param request 多模态视频请求
     * @return 原始快照与结构化提交结果
     */
    public DreaminaCliResponse<DreaminaGenerateSubmit> multimodal2VideoSubmit(
        DreaminaMultimodal2VideoRequest request) {
        return structuredPayloadMapper.mapGenerateSubmit(multimodal2video(request));
    }

    /**
     * 通用逃逸口：在执行任意子命令后映射为「生成提交」视图（若 JSON 不匹配则字段多为空）。
     *
     * @param raw 事先取得的 CLI 快照；不得为 null
     */
    public DreaminaCliResponse<DreaminaGenerateSubmit> mapGenerateSubmitOnly(DreaminaCliResult raw) {
        return structuredPayloadMapper.mapGenerateSubmit(raw);
    }

    /**
     * 通用：映射 {@link DreaminaQueryResult}。
     *
     * @param raw 事先取得的 CLI 快照；不得为 null
     */
    public DreaminaCliResponse<DreaminaQueryResult> mapQueryResultOnly(DreaminaCliResult raw) {
        return structuredPayloadMapper.mapQueryResult(raw);
    }

    /**
     * 通用：映射 {@link List<DreaminaTaskItem>}。
     *
     * @param raw 事先取得的 CLI 快照；不得为 null
     */
    public DreaminaCliResponse<List<DreaminaTaskItem>> mapTaskListOnly(DreaminaCliResult raw) {
        return structuredPayloadMapper.mapTaskList(raw);
    }

    /**
     * 通用：映射 {@link DreaminaHelp}。
     *
     * @param topic 帮助主题；可为 null
     * @param raw   CLI 快照；不得为 null
     */
    public DreaminaCliResponse<DreaminaHelp> mapHelpOnly(String topic, DreaminaCliResult raw) {
        return structuredPayloadMapper.mapHelp(topic, raw);
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
     */
    private DreaminaCliResult run(CommandLine commandLine) {
        long timeoutMs = properties.getCommandTimeoutMillis();
        if (timeoutMs <= 0) {
            throw new IllegalStateException("dreamina.cli.command-timeout-millis must be positive");
        }

        File workingDirectory = resolveWorkingDirectory();
        SubprocessExecutionSupport.ExecutionRequest request =
                new SubprocessExecutionSupport.ExecutionRequest(commandLine, workingDirectory, null, timeoutMs);

        try {
            SubprocessExecutionSupport.RunSession session = executeSubprocess(request);
            return completeAfterWait(
                    commandLine,
                    timeoutMs,
                    session.getStdout(),
                    session.getStderr(),
                    session.getHandler(),
                    session.getWatchdog(),
                    session.isWaitTimedOut(),
                    null);
        } catch (IOException e) {
            throw failedToStart(commandLine, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DreaminaCliException("Interrupted while awaiting Dreamina CLI subprocess", e, null);
        }
    }

    /**
     * 解析并校验工作目录配置。
     */
    private File resolveWorkingDirectory() {
        String wdProperty = properties.getWorkingDirectory();
        if (wdProperty == null || wdProperty.trim().isEmpty()) {
            return null;
        }
        File wd = new File(wdProperty.trim());
        if (!wd.isDirectory()) {
            throw new DreaminaCliExecutableFailureException(
                    "dreamina.cli.working-directory is not an existing directory: " + wd.getAbsolutePath(), null);
        }
        return wd;
    }

    /**
     * 启动子进程（包内可见，供单测注入失败场景）。
     */
    SubprocessExecutionSupport.RunSession executeSubprocess(SubprocessExecutionSupport.ExecutionRequest request)
            throws IOException, InterruptedException {
        return SubprocessExecutionSupport.execute(request);
    }

    /**
     * 创建 {@link #run(CommandLine)} 使用的进程执行器（包内可见，供单测注入抛出 {@link IOException} 的子类）。
     *
     * @deprecated 子进程执行已迁移至 {@link SubprocessExecutionSupport}；保留以兼容旧单测覆写点。
     */
    @Deprecated
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
        boolean waitTimedOut,
        Exception asyncFailureOverride) {
        String stdoutStr = new String(out.toByteArray(), StandardCharsets.UTF_8);
        String stderrStr = new String(err.toByteArray(), StandardCharsets.UTF_8);
        DreaminaParsedFields parsed = DreaminaCliOutputParser.parseBestEffort(stdoutStr, stderrStr);

        // --- 超时：Watchdog 结束进程或 handler 等待超时，优先抛出超时异常 ---
        if (waitTimedOut || watchdog.killedProcess()) {
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
