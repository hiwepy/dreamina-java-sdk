package io.github.hiwepy.dreamina.cli.parser;

import io.github.hiwepy.dreamina.cli.DreaminaCliResult;
import io.github.hiwepy.dreamina.cli.DreaminaDeviceLoginResult;
import io.github.hiwepy.dreamina.cli.DreaminaGenerateSubmitResult;
import io.github.hiwepy.dreamina.cli.DreaminaHelpResult;
import io.github.hiwepy.dreamina.cli.DreaminaLoginResult;
import io.github.hiwepy.dreamina.cli.DreaminaQueryResult;
import io.github.hiwepy.dreamina.cli.DreaminaSessionListResult;
import io.github.hiwepy.dreamina.cli.DreaminaSessionMutationResult;
import io.github.hiwepy.dreamina.cli.DreaminaSessionRow;
import io.github.hiwepy.dreamina.cli.DreaminaSessionSearchResult;
import io.github.hiwepy.dreamina.cli.DreaminaTaskListResult;
import io.github.hiwepy.dreamina.cli.DreaminaUserCreditResult;
import io.github.hiwepy.dreamina.cli.DreaminaVersionResult;
import io.github.hiwepy.dreamina.util.DreaminaStrings;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/**
 * 将 {@link DreaminaCliResult} 映射为领域友好的结构化对象。
 * <p>
 * 解析策略：
 * </p>
 * <ol>
 *   <li>优先识别 stdout/stderr 中的 JSON 片段（对象或数组）。</li>
 *   <li>JSON 失效时保留 {@code raw*} 文本字段并尽量减少臆测。</li>
 *   <li>不依赖具体中文提示文案做关键字段提取（仅登录复用检测允许中英启发式）。</li>
 * </ol>
 *
 * @author wandl
 * @since 1.0.0
 */
@Slf4j
public final class DreaminaCliStructuredPayloadMapper {

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
    public DreaminaVersionResult mapVersion(DreaminaCliResult raw) {
        JsonNode root = tryParseJsonTree(raw);
        String fallback = mergeStreams(raw);
        String version = textField(root, "version");
        String commit = textField(root, "commit");
        String buildTime = textField(root, "build_time");
        return DreaminaVersionResult.builder()
            .version(version)
            .commit(commit)
            .buildTime(buildTime)
            .json(root)
            .rawTextFallback(root == null ? fallback : null)
            .build();
    }

    /**
     * 映射 {@code user_credit}。
     *
     * @param raw CLI 快照；不得为 null
     */
    public DreaminaUserCreditResult mapUserCredit(DreaminaCliResult raw) {
        JsonNode root = tryParseJsonTree(raw);
        String fallback = mergeStreams(raw);
        Long total = longField(root, "total_credit");
        Long uid = longField(root, "user_id");
        String name = textField(root, "user_name");
        String vip = textField(root, "vip_level");
        return DreaminaUserCreditResult.builder()
            .totalCredit(total)
            .userId(uid)
            .userName(name)
            .vipLevel(vip)
            .json(root)
            .rawTextFallback(root == null ? fallback : null)
            .build();
    }

    /**
     * 映射 {@code help}。
     *
     * @param topic       子命令主题；根帮助时 {@code null}
     * @param raw CLI 快照；不得为 null
     */
    public DreaminaHelpResult mapHelp(String topic, DreaminaCliResult raw) {
        return DreaminaHelpResult.builder()
            .topic(topic)
            .fullText(mergeStreams(raw))
            .build();
    }

    /**
     * 映射 {@code list_task} JSON 数组。
     *
     * @param raw CLI 快照；不得为 null
     */
    public DreaminaTaskListResult mapTaskList(DreaminaCliResult raw) {
        JsonNode root = tryParseJsonTree(raw);
        JsonNode array = null;
        Integer count = null;
        if (root != null && root.isArray()) {
            array = root;
            count = root.size();
        }
        return DreaminaTaskListResult.builder()
            .tasks(array)
            .taskCount(count)
            .rawStdout(raw.getStdout())
            .rawStderr(raw.getStderr())
            .build();
    }

    /**
     * 映射 {@code query_result}。
     *
     * @param raw CLI 快照；不得为 null
     */
    public DreaminaQueryResult mapQueryResult(DreaminaCliResult raw) {
        JsonNode root = tryParseJsonTree(raw);
        String fallback = mergeStreams(raw);
        String sid = textField(root, "submit_id");
        String gen = textField(root, "gen_status");
        String fail = textField(root, "fail_reason");
        JsonNode queue = nodeField(root, "queue_info");
        JsonNode result = nodeField(root, "result_json");
        Long credit = longField(root, "credit_count");
        return DreaminaQueryResult.builder()
            .submitId(firstNonBlank(sid, raw.getParsed() != null ? raw.getParsed().getSubmitId() : null))
            .genStatus(gen)
            .failReason(fail)
            .queueInfo(queue)
            .resultJson(result)
            .creditCount(credit)
            .json(root)
            .rawTextFallback(root == null ? fallback : null)
            .build();
    }

    /**
     * 映射异步生成命令的标准返回 JSON。
     *
     * @param raw CLI 快照；不得为 null
     */
    public DreaminaGenerateSubmitResult mapGenerateSubmit(DreaminaCliResult raw) {
        JsonNode root = tryParseJsonTree(raw);
        String fallback = mergeStreams(raw);
        String sid = textField(root, "submit_id");
        String gen = textField(root, "gen_status");
        String fail = textField(root, "fail_reason");
        JsonNode queue = nodeField(root, "queue_info");
        String logId = textField(root, "logid");
        Long credit = longField(root, "credit_count");
        return DreaminaGenerateSubmitResult.builder()
            .submitId(firstNonBlank(sid, raw.getParsed() != null ? raw.getParsed().getSubmitId() : null))
            .genStatus(gen)
            .failReason(fail)
            .queueInfo(queue)
            .logId(logId)
            .creditCount(credit)
            .json(root)
            .rawTextFallback(root == null ? fallback : null)
            .build();
    }

    /**
     * 映射 {@code session list} 表格。
     *
     * @param raw CLI 快照；不得为 null
     */
    public DreaminaSessionListResult mapSessionList(DreaminaCliResult raw) {
        String combined = mergeStreams(raw);
        List<DreaminaSessionRow> rows = parseSessionRows(combined, TableKind.FULL);
        return DreaminaSessionListResult.builder()
            .rows(rows)
            .rawCombinedText(combined)
            .build();
    }

    /**
     * 映射 {@code session search}。
     *
     * @param queryTerm 调用侧关键字快照；可为 null
     * @param raw       CLI 快照；不得为 null
     */
    public DreaminaSessionSearchResult mapSessionSearch(String queryTerm, DreaminaCliResult raw) {
        String combined = mergeStreams(raw);
        // --- session search 为精简三列表格：优先按 SEARCH 形态解析，必要时回退 FULL 正则 ---
        List<DreaminaSessionRow> rows = parseSessionRows(combined, TableKind.SEARCH);
        return DreaminaSessionSearchResult.builder()
            .queryTerm(queryTerm)
            .matches(rows)
            .rawCombinedText(combined)
            .build();
    }

    /**
     * 映射 {@code session create}/{@code rename}。
     *
     * @param raw CLI 快照；不得为 null
     */
    public DreaminaSessionMutationResult mapSessionMutation(DreaminaCliResult raw) {
        String combined = mergeStreams(raw).trim();
        Matcher c = SESSION_CREATED.matcher(combined);
        if (c.matches()) {
            String name = c.group(1);
            String id = c.group(2);
            return DreaminaSessionMutationResult.builder()
                .kind(DreaminaSessionMutationResult.Kind.CREATE)
                .sessionId(id)
                .sessionName(name)
                .messageLine(combined)
                .build();
        }
        Matcher r = SESSION_RENAMED.matcher(combined);
        if (r.matches()) {
            String id = r.group(1);
            String name = r.group(2);
            return DreaminaSessionMutationResult.builder()
                .kind(DreaminaSessionMutationResult.Kind.RENAME)
                .sessionId(id)
                .sessionName(name)
                .messageLine(combined)
                .build();
        }
        return DreaminaSessionMutationResult.builder()
            .kind(DreaminaSessionMutationResult.Kind.UNKNOWN)
            .sessionId(null)
            .sessionName(null)
            .messageLine(combined)
            .build();
    }

    /**
     * 映射 {@code login --headless} / {@code login} 的一般输出。
     *
     * @param raw CLI 快照；不得为 null
     */
    public DreaminaLoginResult mapLogin(DreaminaCliResult raw) {
        String combined = mergeStreams(raw);
        JsonNode root = tryParseJsonTree(raw);
        DreaminaDeviceLoginResult device = mapDeviceLogin(root);
        Boolean reused = detectOAuthReuse(combined);
        return DreaminaLoginResult.builder()
            .combinedText(combined)
            .oauthSessionReused(reused)
            .device(device)
            .json(root)
            .build();
    }

    /**
     * 将 Device Flow JSON 映射为独立对象。
     *
     * @param raw CLI 快照；不得为 null
     */
    public DreaminaDeviceLoginResult mapDeviceLogin(DreaminaCliResult raw) {
        JsonNode root = tryParseJsonTree(raw);
        DreaminaDeviceLoginResult built = mapDeviceLogin(root);
        if (built != null) {
            return built;
        }
        return DreaminaDeviceLoginResult.builder()
            .deviceCode(null)
            .verificationUri(null)
            .userCode(null)
            .json(null)
            .build();
    }

    private DreaminaDeviceLoginResult mapDeviceLogin(JsonNode root) {
        if (root == null || !root.isObject()) {
            return null;
        }
        String dc = firstNonBlank(
            textField(root, "device_code"),
            textField(root, "deviceCode"));
        String vu = firstNonBlank(
            textField(root, "verification_uri"),
            textField(root, "verificationUri"));
        String uc = firstNonBlank(
            textField(root, "user_code"),
            textField(root, "userCode"));
        if (dc == null && vu == null && uc == null) {
            return null;
        }
        return DreaminaDeviceLoginResult.builder()
            .deviceCode(dc)
            .verificationUri(vu)
            .userCode(uc)
            .json(root)
            .build();
    }

    private enum TableKind {
        FULL,
        SEARCH
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
            // --- 跳过引导统计行 / 表头 / 分隔线 ---
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
            if (!seenHeader && (t.startsWith("ID ") || SESSION_LIST_ROW.matcher(t).matches() || SESSION_SEARCH_ROW.matcher(t).matches())) {
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

    private static String textField(JsonNode root, String field) {
        if (root == null || !root.has(field)) {
            return null;
        }
        JsonNode n = root.get(field);
        if (n == null || n.isNull()) {
            return null;
        }
        if (n.isTextual()) {
            String v = n.asText();
            return v.isEmpty() ? "" : v;
        }
        if (n.isNumber()) {
            return n.canConvertToLong() ? Long.toString(n.longValue()) : n.decimalValue().toPlainString();
        }
        if (n.isBoolean()) {
            return Boolean.toString(n.booleanValue());
        }
        return n.toString();
    }

    private static Long longField(JsonNode root, String field) {
        if (root == null || !root.has(field)) {
            return null;
        }
        JsonNode n = root.get(field);
        if (n == null || n.isNull()) {
            return null;
        }
        if (n.isIntegralNumber()) {
            return n.longValue();
        }
        if (n.isTextual()) {
            try {
                return Long.parseLong(n.asText().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static JsonNode nodeField(JsonNode root, String field) {
        if (root == null || !root.has(field)) {
            return null;
        }
        JsonNode n = root.get(field);
        return (n == null || n.isNull()) ? null : n;
    }

    private static String firstNonBlank(String a, String b) {
        if (DreaminaStrings.isNotBlank(a)) {
            return a;
        }
        if (DreaminaStrings.isNotBlank(b)) {
            return b;
        }
        return null;
    }

    /**
     * 启发式检测「复用现有 OAuth」语义（中英兜底）。
     */
    private static Boolean detectOAuthReuse(String combined) {
        if (DreaminaStrings.isBlank(combined)) {
            return null;
        }
        String lower = combined.toLowerCase(Locale.ROOT);
        if (combined.contains("复用") && combined.contains("登录")) {
            return true;
        }
        if (lower.contains("reuse") && lower.contains("oauth")) {
            return true;
        }
        if (lower.contains("already logged") || lower.contains("still valid")) {
            return true;
        }
        return null;
    }
}
