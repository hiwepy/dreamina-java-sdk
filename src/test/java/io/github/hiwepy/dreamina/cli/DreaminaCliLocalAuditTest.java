package io.github.hiwepy.dreamina.cli;

import io.github.hiwepy.dreamina.DreaminaCliProperties;
import io.github.hiwepy.dreamina.cli.model.DreaminaSessionList;
import io.github.hiwepy.dreamina.cli.model.DreaminaSessionRow;
import io.github.hiwepy.dreamina.cli.model.DreaminaTaskItem;
import io.github.hiwepy.dreamina.cli.model.DreaminaUserCredit;
import io.github.hiwepy.dreamina.cli.model.DreaminaVersion;
import io.github.hiwepy.dreamina.exception.DreaminaCliException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 在已安装 {@code dreamina} 的本机执行只读验收（需已登录，否则跳过）。
 * <p>
 * 启用方式：
 * </p>
 * <pre>
 * export DREAMINA_CLI_AUDIT=true
 * mvn -q test -Dtest=DreaminaCliLocalAuditTest -DskipTests=false
 * </pre>
 * <p>
 * 查看参数：{@code dreamina &lt;cmd&gt; -h} 或 {@code dreamina help &lt;cmd&gt;}；
 * 二级子命令示例 {@code dreamina session list -h}。
 * {@code dreamina session help} 在新版 CLI 上等价于 session 根帮助（exit 0）；
 * 生成类命令的 {@code dreamina text2image help} 仍可能无输出，优先用 {@code text2image -h}。
 * </p>
 * <p>
 * 本地审计<strong>不会</strong>执行 {@code logout}、{@code relogin}、{@code login}，以免打断 OAuth。
 * 曾有的 {@code DREAMINA_CLI_AUDIT_LOGIN} 登录验收已移除。
 * </p>
 *
 * @author wandl
 * @since 1.0.0
 */
@Tag("dreamina-local")
@EnabledIfEnvironmentVariable(named = "DREAMINA_CLI_AUDIT", matches = "true")
class DreaminaCliLocalAuditTest {

    private DreaminaCliExecutor executor;

    /**
     * 构造执行器并确认 CLI 可用且已登录。
     */
    @BeforeEach
    void setUp() {
        String exe = System.getenv("DREAMINA_CLI_EXECUTABLE");
        if (exe == null || exe.isBlank()) {
            exe = "dreamina";
        }
        DreaminaCliProperties props = new DreaminaCliProperties();
        props.setExecutable(exe);
        props.setCommandTimeoutMillis(120_000L);
        executor = new DreaminaCliExecutor(props);
        assumeTrue(executor.version().isSuccess(), "dreamina version failed");
        try {
            DreaminaCliResponse<DreaminaUserCredit> credit = executor.userCreditInfo();
            assumeTrue(credit.isSuccess(), "dreamina user_credit failed — run `dreamina login` first");
        } catch (DreaminaCliException ex) {
            assumeTrue(false, "dreamina user_credit failed — run `dreamina login` first: " + ex.getMessage());
        }
    }

    /**
     * {@code version} JSON。
     */
    @Test
    void auditVersion() {
        DreaminaCliResponse<DreaminaVersion> r = executor.versionInfo();
        assertTrue(r.isSuccess());
        assertNotNull(r.getBody());
        assertNotNull(r.getBody().getVersion());
        assertNotNull(r.getJson());
    }

    /**
     * {@code user_credit} JSON。
     */
    @Test
    void auditUserCredit() {
        DreaminaCliResponse<DreaminaUserCredit> r = executor.userCreditInfo();
        assertTrue(r.isSuccess());
        assertNotNull(r.getBody());
        assertNotNull(r.getBody().getTotalCredit());
        assertNotNull(r.getBody().getUserId());
        assertNotNull(r.getBody().getVipLevel());
    }

    /**
     * 根帮助与各子命令 {@code help &lt;topic&gt;}。
     */
    @Test
    void auditHelpTopics() {
        assertTrue(executor.helpInfo().getCombinedText().contains("即梦"));
        assertTrue(executor.helpInfo("text2image").getCombinedText().contains("text2image"));
        assertTrue(executor.helpInfo("session").getCombinedText().contains("Manage Dreamina sessions"));
        assertTrue(executor.helpInfo("list_task").getCombinedText().contains("list_task"));
    }

    /**
     * {@code session} 无子命令 → 帮助正文。
     */
    @Test
    void auditSessionBare() {
        DreaminaCliResponse<?> r = executor.sessionInfo();
        assertTrue(r.isSuccess());
        assertTrue(r.getCombinedText().contains("Available Commands"));
    }

    /**
     * {@code session list -n 5} 表格（需登录且有会话数据）。
     */
    @Test
    void auditSessionList() {
        DreaminaCliResponse<DreaminaSessionList> r =
            executor.sessionListInfo(List.of("-n", "5"));
        assumeTrue(r.isSuccess(), "session list failed: " + r.getCombinedText());
        List<DreaminaSessionRow> rows = r.getBody().safeRows();
        assumeTrue(!rows.isEmpty(), "session list returned no rows (empty account?)");
        assertTrue(rows.stream().anyMatch(row -> "0".equals(row.getId())));
    }

    /**
     * {@code list_task --limit 3} JSON 数组。
     */
    @Test
    void auditListTask() {
        DreaminaCliResponse<List<DreaminaTaskItem>> r =
            executor.listTaskInfo(List.of("--limit", "3"));
        assumeTrue(r.isSuccess(), "list_task failed: " + r.getCombinedText());
        assertNotNull(r.getBody());
        assumeTrue(!r.getBody().isEmpty(), "list_task returned empty array");
        assertNotNull(r.getBody().get(0).getSubmitId());
        assertNotNull(r.getBody().get(0).getGenStatus());
    }


    /**
     * {@code session search "default"} 表格（需登录）。
     */
    @Test
    void auditSessionSearch() {
        DreaminaCliResponse<?> r = executor.sessionSearchInfo("default");
        assumeTrue(r.isSuccess(), "session search failed: " + r.getCombinedText());
        assertTrue(r.getCombinedText().contains("default") || !r.getCombinedText().isBlank());
    }

    /**
     * {@code query_result} 对 list_task 首条 submit_id（需登录且有历史任务）。
     */
    @Test
    void auditQueryResultFromListTask() {
        DreaminaCliResponse<List<DreaminaTaskItem>> list =
            executor.listTaskInfo(List.of("--limit", "1"));
        assumeTrue(list.isSuccess() && list.getBody() != null && !list.getBody().isEmpty(),
            "list_task empty — skip query_result audit");
        String submitId = list.getBody().get(0).getSubmitId();
        assumeTrue(submitId != null && !submitId.isBlank());
        DreaminaCliResponse<?> qr = executor.queryResultInfo(submitId);
        assumeTrue(qr.isSuccess(), "query_result failed: " + qr.getCombinedText());
        assertNotNull(qr.getBody());
    }

    /**
     * {@code session create -h} 帮助（推荐方式，非 {@code session create help}）。
     */
    @Test
    void auditSessionCreateHelpViaDashH() {
        DreaminaCliResult raw = executor.session(List.of("create", "-h"));
        assertTrue(raw.isSuccess());
        assertTrue(raw.getStdout().contains("session create"));
    }

    /**
     * 新版 CLI：{@code dreamina session help} 常等价于 session 根帮助。
     */
    @Test
    void auditSessionHelpAlias() {
        DreaminaCliResult raw = executor.session(List.of("help"));
        assertTrue(raw.isSuccess());
        assertTrue(raw.getStdout().contains("Manage Dreamina sessions"));
    }
}
