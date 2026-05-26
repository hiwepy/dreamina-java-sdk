package io.github.hiwepy.dreamina.cli.parser;

import io.github.hiwepy.dreamina.cli.DreaminaCliResponse;
import io.github.hiwepy.dreamina.cli.DreaminaCliResult;
import io.github.hiwepy.dreamina.cli.model.DreaminaCheckLogin;
import io.github.hiwepy.dreamina.cli.model.DreaminaDeviceLogin;
import io.github.hiwepy.dreamina.cli.model.DreaminaGenerateSubmit;
import io.github.hiwepy.dreamina.cli.model.DreaminaHelp;
import io.github.hiwepy.dreamina.cli.model.DreaminaLogin;
import io.github.hiwepy.dreamina.cli.model.DreaminaLoginAccount;
import io.github.hiwepy.dreamina.cli.model.DreaminaLogout;
import io.github.hiwepy.dreamina.cli.model.DreaminaQueryResult;
import io.github.hiwepy.dreamina.cli.model.DreaminaRelogin;
import io.github.hiwepy.dreamina.cli.model.DreaminaQueueInfoSupport;
import io.github.hiwepy.dreamina.cli.model.DreaminaSessionDelete;
import io.github.hiwepy.dreamina.cli.model.DreaminaSessionList;
import io.github.hiwepy.dreamina.cli.model.DreaminaSessionMutation;
import io.github.hiwepy.dreamina.cli.model.DreaminaSessionRow;
import io.github.hiwepy.dreamina.cli.model.DreaminaSessionSearch;
import io.github.hiwepy.dreamina.cli.model.DreaminaTaskItem;
import io.github.hiwepy.dreamina.cli.model.DreaminaUserCredit;
import io.github.hiwepy.dreamina.cli.model.DreaminaVersion;
import io.github.hiwepy.dreamina.util.DreaminaStrings;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/**
 * 将 {@link DreaminaCliResult} 转为 {@link DreaminaCliResponse}（原始输出 + 解析体 {@code body}）。
 * <p>
 * JSON 命令：{@code body} 为与 CLI 字段对应的 {@code cli.model} 类型；文本/表格命令同理。
 * </p>
 *
 * @author wandl
 * @since 1.0.0
 */
@Slf4j
public final class DreaminaCliStructuredPayloadMapper {

    private static final TypeReference<List<DreaminaTaskItem>> TASK_LIST_TYPE =
        new TypeReference<>() {};

    private static final Pattern SESSION_LIST_ROW = Pattern.compile(
        "^(?<id>\\d+)\\s+(?<name>.+?)\\s+(?<pinned>Yes|No)\\s+(?<updated>\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2})$");

    private static final Pattern SESSION_SEARCH_ROW = Pattern.compile(
        "^(?<id>\\d+)\\s+(?<name>.+?)\\s+(?<updated>\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2})$");

    private static final Pattern SESSION_CREATED = Pattern.compile(
        "Created\\s+session\\s+\"([^\"]+)\"\\s+\\(ID:\\s*(\\d+)\\)\\s*", Pattern.CASE_INSENSITIVE);

    private static final Pattern SESSION_RENAMED = Pattern.compile(
        "Renamed\\s+session\\s+(\\d+)\\s+to\\s+\"([^\"]+)\"\\s*", Pattern.CASE_INSENSITIVE);

    private final ObjectMapper objectMapper;

    /**
     * 默认构造函数：构造宽松 JSON 映射器。
     */
    public DreaminaCliStructuredPayloadMapper() {
        this(defaultObjectMapper());
    }

    /**
     * 允许测试注入自定义 {@link ObjectMapper}。
     *
     * @param objectMapper Jackson 映射器；不得为 null
     */
    public DreaminaCliStructuredPayloadMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Dreamina 模块默认的 Jackson 配置（忽略未知字段，兼容 CLI 演进）。
     *
     * @return 新的映射器实例
     */
    public static ObjectMapper defaultObjectMapper() {
        ObjectMapper om = new ObjectMapper();
        om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return om;
    }

    /**
     * 映射 {@code version} 命令输出。
     *
     * @param raw CLI 快照；不得为 null
     */
    public DreaminaCliResponse<DreaminaVersion> mapVersion(DreaminaCliResult raw) {
        JsonNode root = tryParseJsonTree(raw);
        return DreaminaCliResponse.of(raw, readPayload(root, DreaminaVersion.class), root);
    }

    /**
     * 映射 {@code user_credit}。
     *
     * @param raw CLI 快照；不得为 null
     */
    public DreaminaCliResponse<DreaminaUserCredit> mapUserCredit(DreaminaCliResult raw) {
        JsonNode root = tryParseJsonTree(raw);
        return DreaminaCliResponse.of(raw, readPayload(root, DreaminaUserCredit.class), root);
    }

    /**
     * 映射 {@code help}。
     *
     * @param topic 子命令主题；根帮助时 {@code null}
     * @param raw   CLI 快照；不得为 null
     */
    public DreaminaCliResponse<DreaminaHelp> mapHelp(String topic, DreaminaCliResult raw) {
        return DreaminaCliResponse.of(raw, DreaminaHelp.builder().topic(topic).build(), null);
    }

    /**
     * 映射 {@code logout}。
     *
     * @param raw CLI 快照；不得为 null
     */
    public DreaminaCliResponse<DreaminaLogout> mapLogout(DreaminaCliResult raw) {
        boolean cleared = DreaminaLoginTextParser.detectsLogoutCleared(combinedText(raw));
        return DreaminaCliResponse.of(
            raw,
            DreaminaLogout.builder().localSessionCleared(cleared ? Boolean.TRUE : null).build(),
            null);
    }

    /**
     * 映射 {@code relogin}。
     *
     * @param raw CLI 快照；不得为 null
     */
    public DreaminaCliResponse<DreaminaRelogin> mapRelogin(DreaminaCliResult raw) {
        JsonNode root = tryParseJsonTree(raw);
        DreaminaDeviceLogin device = resolveDeviceLogin(raw);
        boolean browser = DreaminaLoginTextParser.detectsDeviceFlowBrowserPrompt(combinedText(raw));
        Boolean requiresBrowser = browser || (device != null && device.isMaterialPresent()) ? Boolean.TRUE : null;
        return DreaminaCliResponse.of(
            raw,
            DreaminaRelogin.builder().requiresBrowserOAuth(requiresBrowser).device(device).build(),
            root);
    }

    /**
     * 映射 {@code login checklogin} JSON。
     *
     * @param raw CLI 快照；不得为 null
     */
    public DreaminaCliResponse<DreaminaCheckLogin> mapCheckLogin(DreaminaCliResult raw) {
        JsonNode root = tryParseJsonTree(raw);
        return DreaminaCliResponse.of(raw, readPayload(root, DreaminaCheckLogin.class), root);
    }

    /**
     * 映射 {@code list_task} JSON 数组。
     *
     * @param raw CLI 快照；不得为 null
     */
    public DreaminaCliResponse<List<DreaminaTaskItem>> mapTaskList(DreaminaCliResult raw) {
        JsonNode root = tryParseJsonTree(raw);
        return DreaminaCliResponse.of(raw, readTaskList(root), root);
    }

    /**
     * 映射 {@code query_result}。
     *
     * @param raw CLI 快照；不得为 null
     */
    public DreaminaCliResponse<DreaminaQueryResult> mapQueryResult(DreaminaCliResult raw) {
        JsonNode root = tryParseJsonTree(raw);
        DreaminaQueryResult body = readPayload(root, DreaminaQueryResult.class);
        if (body != null) {
            DreaminaQueueInfoSupport.enrichParsedDebugInfo(objectMapper, body.getQueueInfo());
            if (DreaminaStrings.isBlank(body.getSubmitId())
                && raw.getParsed() != null
                && DreaminaStrings.isNotBlank(raw.getParsed().getSubmitId())) {
                body.setSubmitId(raw.getParsed().getSubmitId());
            }
        }
        return DreaminaCliResponse.of(raw, body, root);
    }

    /**
     * 映射异步生成命令的标准返回 JSON。
     *
     * @param raw CLI 快照；不得为 null
     */
    public DreaminaCliResponse<DreaminaGenerateSubmit> mapGenerateSubmit(DreaminaCliResult raw) {
        JsonNode root = tryParseJsonTree(raw);
        DreaminaGenerateSubmit body = readPayload(root, DreaminaGenerateSubmit.class);
        if (body != null) {
            DreaminaQueueInfoSupport.enrichParsedDebugInfo(objectMapper, body.getQueueInfo());
            if (DreaminaStrings.isBlank(body.getSubmitId())
                && raw.getParsed() != null
                && DreaminaStrings.isNotBlank(raw.getParsed().getSubmitId())) {
                body.setSubmitId(raw.getParsed().getSubmitId());
            }
        }
        return DreaminaCliResponse.of(raw, body, root);
    }

    /**
     * 映射 {@code session list} 表格。
     *
     * @param raw CLI 快照；不得为 null
     */
    public DreaminaCliResponse<DreaminaSessionList> mapSessionList(DreaminaCliResult raw) {
        List<DreaminaSessionRow> rows = parseSessionRows(combinedText(raw), TableKind.FULL);
        return DreaminaCliResponse.of(raw, DreaminaSessionList.builder().rows(rows).build(), null);
    }

    /**
     * 映射 {@code session search}。
     *
     * @param queryTerm 调用侧关键字快照；可为 null
     * @param raw       CLI 快照；不得为 null
     */
    public DreaminaCliResponse<DreaminaSessionSearch> mapSessionSearch(String queryTerm, DreaminaCliResult raw) {
        List<DreaminaSessionRow> rows = parseSessionRows(combinedText(raw), TableKind.SEARCH);
        return DreaminaCliResponse.of(
            raw,
            DreaminaSessionSearch.builder().queryTerm(queryTerm).rows(rows).build(),
            null);
    }

    /**
     * 映射 {@code session delete}/{@code rm}。
     *
     * @param raw CLI 快照；不得为 null
     */
    public DreaminaCliResponse<DreaminaSessionDelete> mapSessionDelete(DreaminaCliResult raw) {
        boolean deleted = combinedText(raw).trim().equalsIgnoreCase("deleted");
        return DreaminaCliResponse.of(raw, DreaminaSessionDelete.builder().deleted(deleted).build(), null);
    }

    /**
     * 映射 {@code session create}/{@code rename}。
     *
     * @param raw CLI 快照；不得为 null
     */
    public DreaminaCliResponse<DreaminaSessionMutation> mapSessionMutation(DreaminaCliResult raw) {
        String combined = combinedText(raw).trim();
        Matcher c = SESSION_CREATED.matcher(combined);
        if (c.matches()) {
            return DreaminaCliResponse.of(
                raw,
                DreaminaSessionMutation.builder()
                    .kind(DreaminaSessionMutation.Kind.CREATE)
                    .sessionId(c.group(2))
                    .sessionName(c.group(1))
                    .build(),
                null);
        }
        Matcher r = SESSION_RENAMED.matcher(combined);
        if (r.matches()) {
            return DreaminaCliResponse.of(
                raw,
                DreaminaSessionMutation.builder()
                    .kind(DreaminaSessionMutation.Kind.RENAME)
                    .sessionId(r.group(1))
                    .sessionName(r.group(2))
                    .build(),
                null);
        }
        return DreaminaCliResponse.of(
            raw,
            DreaminaSessionMutation.builder().kind(DreaminaSessionMutation.Kind.UNKNOWN).build(),
            null);
    }

    /**
     * 映射 {@code login --headless} / {@code login} 的一般输出。
     *
     * @param raw CLI 快照；不得为 null
     */
    public DreaminaCliResponse<DreaminaLogin> mapLogin(DreaminaCliResult raw) {
        JsonNode root = tryParseJsonTree(raw);
        String combined = combinedText(raw);
        boolean reuseDetected = DreaminaLoginTextParser.detectsOAuthReuse(combined);
        DreaminaLoginAccount account = DreaminaLoginTextParser.parseReusedAccount(combined);
        Boolean reusedFlag = reuseDetected ? Boolean.TRUE : (account != null ? Boolean.TRUE : null);
        DreaminaLogin body = DreaminaLogin.builder()
            .oauthSessionReused(reusedFlag)
            .account(account)
            .device(resolveDeviceLogin(raw))
            .build();
        return DreaminaCliResponse.of(raw, body, root);
    }

    /**
     * 映射 Device Flow 材料。
     */
    public DreaminaCliResponse<DreaminaDeviceLogin> mapDeviceLogin(DreaminaCliResult raw) {
        JsonNode root = tryParseJsonTree(raw);
        return DreaminaCliResponse.of(raw, resolveDeviceLogin(raw), root);
    }

    /**
     * 解析 Device Flow：优先 JSON，其次键值对文本。
     */
    private DreaminaDeviceLogin resolveDeviceLogin(DreaminaCliResult raw) {
        JsonNode root = tryParseJsonTree(raw);
        DreaminaDeviceLogin fromJson = readPayload(root, DreaminaDeviceLogin.class);
        if (DreaminaLoginTextParser.hasDeviceFlowMaterial(fromJson)) {
            return fromJson;
        }
        DreaminaDeviceLogin fromText = DreaminaLoginTextParser.parseDeviceFlow(combinedText(raw));
        return DreaminaLoginTextParser.hasDeviceFlowMaterial(fromText) ? fromText : null;
    }

    private static String combinedText(DreaminaCliResult raw) {
        return DreaminaCliResponse.of(raw, null).getCombinedText();
    }

    private enum TableKind {
        FULL,
        SEARCH
    }

    /**
     * 使用 Jackson 将 JSON 根节点反序列化为指定类型。
     *
     * @param root JSON 根；可为 null
     * @param type 目标类型
     * @param <T>  类型参数
     * @return 负载或 null
     */
    private <T> T readPayload(JsonNode root, Class<T> type) {
        return treeToValue(root, type);
    }

    /**
     * 将 JSON 数组根反序列化为任务列表。
     *
     * @param root JSON 根
     * @return 任务列表或 null
     */
    private List<DreaminaTaskItem> readTaskList(JsonNode root) {
        if (root == null || !root.isArray()) {
            return null;
        }
        try {
            return objectMapper.convertValue(root, TASK_LIST_TYPE);
        } catch (Exception ex) {
            log.trace("Dreamina task list convert failed snippet={}", summarized(root.toString()), ex);
            return null;
        }
    }

    /**
     * 解析会话表格行为统一的行列表。
     *
     * @param combined 文本
     * @param kind     表格形态
     */
    private List<DreaminaSessionRow> parseSessionRows(String combined, TableKind kind) {
        List<DreaminaSessionRow> rows = new ArrayList<>();
        boolean seenHeader = false;
        try (BufferedReader br = new BufferedReader(new StringReader(combined))) {
            String line;
            while ((line = br.readLine()) != null) {
                String t = line.trim();
                if (t.isEmpty()) {
                    continue;
                }
                if (t.startsWith("Found ") && t.contains("sessions")) {
                    continue;
                }
                if (t.startsWith("ID ") && t.contains("NAME")) {
                    seenHeader = true;
                    continue;
                }
                if (t.startsWith("--")) {
                    seenHeader = true;
                    continue;
                }
                if (!seenHeader
                    && (t.startsWith("ID ")
                        || SESSION_LIST_ROW.matcher(t).matches()
                        || SESSION_SEARCH_ROW.matcher(t).matches())) {
                    seenHeader = true;
                }
                if (!seenHeader) {
                    continue;
                }
                DreaminaSessionRow row = tryParseSessionRow(t, kind);
                if (row != null) {
                    rows.add(row);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("unexpected IO while parsing session rows", e);
        }
        return rows;
    }

    /**
     * 单行解析：优先完整四列，其次搜索三列。
     */
    private DreaminaSessionRow tryParseSessionRow(String line, TableKind preferred) {
        Matcher full = SESSION_LIST_ROW.matcher(line);
        Matcher search = SESSION_SEARCH_ROW.matcher(line);
        if (preferred == TableKind.FULL && full.matches()) {
            return DreaminaSessionRow.builder()
                .id(full.group("id"))
                .name(full.group("name").trim())
                .pinned(full.group("pinned"))
                .updatedAt(full.group("updated"))
                .build();
        }
        if (preferred == TableKind.SEARCH && search.matches()) {
            return DreaminaSessionRow.builder()
                .id(search.group("id"))
                .name(search.group("name").trim())
                .pinned(null)
                .updatedAt(search.group("updated"))
                .build();
        }
        if (full.matches()) {
            return DreaminaSessionRow.builder()
                .id(full.group("id"))
                .name(full.group("name").trim())
                .pinned(full.group("pinned"))
                .updatedAt(full.group("updated"))
                .build();
        }
        if (search.matches()) {
            return DreaminaSessionRow.builder()
                .id(search.group("id"))
                .name(search.group("name").trim())
                .pinned(null)
                .updatedAt(search.group("updated"))
                .build();
        }
        return null;
    }

    /**
     * 尝试解析 JSON：优先 stdout，其次 bracket 扫描合并文本。
     */
    private JsonNode tryParseJsonTree(DreaminaCliResult raw) {
        JsonNode direct = tryParseSingle(raw.getStdout());
        if (direct != null) {
            return direct;
        }
        JsonNode fromCombined = tryParseSingle(DreaminaCliJsonExtract.extractFirstBalancedJson(mergeStreams(raw)));
        if (fromCombined != null) {
            return fromCombined;
        }
        return tryParseSingle(raw.getStderr());
    }

    private JsonNode tryParseSingle(String payload) {
        if (DreaminaStrings.isBlank(payload)) {
            return null;
        }
        String trimmed = payload.trim();
        try {
            return objectMapper.readTree(trimmed);
        } catch (Exception ignored) {
            String extracted = DreaminaCliJsonExtract.extractFirstBalancedJson(trimmed);
            if (extracted == null) {
                return null;
            }
            try {
                return objectMapper.readTree(extracted);
            } catch (Exception ex) {
                log.trace("Dreamina JSON parse failed snippet={}", summarized(extracted), ex);
                return null;
            }
        }
    }

    private static String summarized(String s) {
        if (s.length() <= 256) {
            return s;
        }
        return s.substring(0, 256) + "...";
    }

    private static String mergeStreams(DreaminaCliResult raw) {
        String out = raw.getStdout() == null ? "" : raw.getStdout();
        String err = raw.getStderr() == null ? "" : raw.getStderr();
        if (err.isEmpty()) {
            return out;
        }
        if (out.isEmpty()) {
            return err;
        }
        return out + "\n" + err;
    }

    /**
     * 将 JSON 子树反序列化为强类型对象；节点缺失或解析失败时返回 {@code null}。
     */
    private <T> T treeToValue(JsonNode node, Class<T> type) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return objectMapper.treeToValue(node, type);
        } catch (Exception ex) {
            log.trace(
                "Dreamina treeToValue failed type={} snippet={}",
                type.getSimpleName(),
                summarized(node.toString()),
                ex);
            return null;
        }
    }

}
