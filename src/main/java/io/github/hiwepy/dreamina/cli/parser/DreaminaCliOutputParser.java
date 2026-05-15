package io.github.hiwepy.dreamina.cli.parser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dreamina CLI stdout / stderr 的轻量正则解析。
 * <p>
 * 仅抽取与后续编排弱耦合的字段；任何匹配失败时返回各字段均为默认 null 的
 * {@link DreaminaParsedFields}，调用方必须使用原始字符串做降级。
 * </p>
 *
 * @author wandl
 * @since 1.0.0
 */
public final class DreaminaCliOutputParser {

    private static final Pattern SUBMIT_ID_PATTERN = Pattern.compile(
        "(?:--submit[_-]?id=|^\\s*submit[_-]?id\\s*[:=]\\s*)(\\S+)",
        Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    private static final Pattern SUBMIT_ID_ALT = Pattern.compile(
        "\\bsubmit[_-]?id\\b\\s*[:=]\\s*[\"']?([A-Za-z0-9_-]+)", Pattern.CASE_INSENSITIVE);

    /**
     * 任务提交 / 查询等命令输出的紧凑 JSON 中的 {@code "submit_id":"..."}（优先于松散文本匹配）。
     */
    private static final Pattern SUBMIT_ID_JSON = Pattern.compile(
        "\"submit_id\"\\s*:\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);

    private static final Pattern CREDIT_PATTERN = Pattern.compile(
        "(?:(?:user[_-]?)?credits?)\\s*[:=]\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    /**
     * {@code user_credit} 等命令返回的 JSON 中的额度字段（键名与数字之间允许空白）。
     */
    private static final Pattern CREDIT_TOTAL_JSON = Pattern.compile(
        "\"total_credit\"\\s*:\\s*(\\d+)\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern POLL_HINT = Pattern.compile(
        "\\b(pending|queued|running)\\b", Pattern.CASE_INSENSITIVE);

    private DreaminaCliOutputParser() {
    }

    /**
     * 合并 stdout/stderr 后做正则扫描，抽取 submitId、credit、polling 暗示。
     *
     * @param stdout 标准输出
     * @param stderr 标准错误
     * @return 非 null 的快照对象；字段可为 null 表示未能识别
     */
    public static DreaminaParsedFields parseBestEffort(String stdout, String stderr) {
        String a = stdout == null ? "" : stdout;
        String b = stderr == null ? "" : stderr;
        String combined = a + "\n" + b;

        // --- 结构化字段：顺序尝试多种 submit_id / credit 表达方式 ---
        String submitId = findSubmitId(combined);

        Long credit = parseCreditLong(combined);

        Boolean pollRecommended = null;
        if (POLL_HINT.matcher(combined).find()) {
            pollRecommended = true;
        }

        return DreaminaParsedFields.builder()
            .submitId(submitId)
            .credit(credit)
            .pollRecommended(pollRecommended)
            .build();
    }

    /**
     * 在多组模式中尝试提取 submit ID。
     */
    private static String findSubmitId(String combined) {
        Matcher json = SUBMIT_ID_JSON.matcher(combined);
        if (json.find()) {
            String id = json.group(1).trim();
            return id.isEmpty() ? null : id;
        }
        Matcher m = SUBMIT_ID_PATTERN.matcher(combined);
        if (m.find()) {
            return trimQuotes(m.group(1));
        }
        Matcher alt = SUBMIT_ID_ALT.matcher(combined);
        if (alt.find()) {
            return trimQuotes(alt.group(1));
        }
        return null;
    }

    /**
     * 从合并输出中解析额度数值：优先匹配 JSON {@code total_credit}，再回退到键值/纯文本形式。
     *
     * @param combined stdout 与 stderr 合并文本
     * @return 解析成功时的非负整数；无法识别时为 {@code null}
     */
    private static Long parseCreditLong(String combined) {
        Matcher json = CREDIT_TOTAL_JSON.matcher(combined);
        if (json.find()) {
            try {
                return Long.parseLong(json.group(1));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        Matcher creditMatcher = CREDIT_PATTERN.matcher(combined);
        if (creditMatcher.find()) {
            try {
                return Long.parseLong(creditMatcher.group(1));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * 去掉首尾配对引号，避免残留 JSON / shell 风格的包裹字符。
     */
    private static String trimQuotes(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        String t = raw.trim();
        if (t.length() >= 2 && ((t.startsWith("\"") && t.endsWith("\"")) || (t.startsWith("'") && t.endsWith("'")))) {
            return t.substring(1, t.length() - 1).trim();
        }
        return t.isEmpty() ? null : t;
    }
}
