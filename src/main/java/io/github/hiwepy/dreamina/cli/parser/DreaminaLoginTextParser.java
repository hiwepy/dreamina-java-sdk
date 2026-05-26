package io.github.hiwepy.dreamina.cli.parser;

import io.github.hiwepy.dreamina.cli.model.DreaminaDeviceLogin;
import io.github.hiwepy.dreamina.cli.model.DreaminaLoginAccount;
import io.github.hiwepy.dreamina.util.DreaminaStrings;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析 {@code dreamina login} / {@code relogin} / {@code logout} 的纯文本输出。
 *
 * @author wandl
 * @since 1.0.0
 */
public final class DreaminaLoginTextParser {

    private static final Pattern KV_LINE = Pattern.compile(
        "^(?<key>[a-z_][a-z0-9_]*)\\s*:\\s*(?<value>.+)$",
        Pattern.CASE_INSENSITIVE);

    private DreaminaLoginTextParser() {
    }

    /**
     * 是否包含「复用本地 OAuth 登录态」语义（中英）。
     *
     * @param combined stdout/stderr 合并文本
     * @return 检测到复用语义为 true
     */
    public static boolean detectsOAuthReuse(String combined) {
        if (DreaminaStrings.isBlank(combined)) {
            return false;
        }
        if (combined.contains("复用") && combined.contains("登录")) {
            return true;
        }
        String lower = combined.toLowerCase(Locale.ROOT);
        return (lower.contains("reuse") && lower.contains("oauth"))
            || lower.contains("already logged")
            || lower.contains("still valid");
    }

    /**
     * 是否包含「已清除本地登录态」语义。
     *
     * @param combined 合并文本
     * @return 检测到登出成功提示为 true
     */
    public static boolean detectsLogoutCleared(String combined) {
        if (DreaminaStrings.isBlank(combined)) {
            return false;
        }
        return combined.contains("已清除") && combined.contains("登录态");
    }

    /**
     * 是否提示需通过浏览器完成 Device Flow（如 {@code relogin} 首行）。
     *
     * @param combined 合并文本
     * @return 需要浏览器 OAuth 为 true
     */
    public static boolean detectsDeviceFlowBrowserPrompt(String combined) {
        if (DreaminaStrings.isBlank(combined)) {
            return false;
        }
        return combined.contains("OAuth Device Flow")
            || combined.contains("请使用浏览器");
    }

    /**
     * 从「当前登录账户信息」键值对段落解析账户摘要。
     *
     * @param combined CLI 合并文本
     * @return 至少解析出一项字段时返回对象，否则 null
     */
    public static DreaminaLoginAccount parseReusedAccount(String combined) {
        if (DreaminaStrings.isBlank(combined)) {
            return null;
        }
        DreaminaLoginAccount payload = new DreaminaLoginAccount();
        boolean any = false;
        for (String line : combined.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            Matcher m = KV_LINE.matcher(trimmed);
            if (!m.matches()) {
                continue;
            }
            String key = m.group("key").toLowerCase(Locale.ROOT);
            String value = m.group("value").trim();
            switch (key) {
                case "user_id" -> {
                    Long uid = parseLong(value);
                    if (uid != null) {
                        payload.setUserId(uid);
                        any = true;
                    }
                }
                case "vip_level" -> {
                    payload.setVipLevel(value);
                    any = true;
                }
                case "total_credit" -> {
                    Long credit = parseLong(value);
                    if (credit != null) {
                        payload.setTotalCredit(credit);
                        any = true;
                    }
                }
                default -> {
                    // 账户段仅识别上述键
                }
            }
        }
        return any ? payload : null;
    }

    /**
     * 从键值对文本解析 Device Flow 材料（{@code relogin} / 部分 {@code --headless} 场景）。
     *
     * @param combined CLI 合并文本
     * @return 至少含 device_code / verification_uri / user_code 之一时返回对象，否则 null
     */
    public static DreaminaDeviceLogin parseDeviceFlow(String combined) {
        if (DreaminaStrings.isBlank(combined)) {
            return null;
        }
        DreaminaDeviceLogin payload = new DreaminaDeviceLogin();
        boolean any = false;
        for (String line : combined.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            Matcher m = KV_LINE.matcher(trimmed);
            if (!m.matches()) {
                continue;
            }
            String key = m.group("key").toLowerCase(Locale.ROOT);
            String value = m.group("value").trim();
            switch (key) {
                case "device_code" -> {
                    payload.setDeviceCode(value);
                    any = true;
                }
                case "verification_uri" -> {
                    payload.setVerificationUri(value);
                    any = true;
                }
                case "user_code" -> {
                    payload.setUserCode(value);
                    any = true;
                }
                case "poll_interval" -> {
                    payload.setPollInterval(value);
                    any = true;
                }
                case "expires_at" -> {
                    payload.setExpiresAt(value);
                    any = true;
                }
                default -> {
                    // 忽略无关键（如 user_id 由 parseReusedAccount 处理）
                }
            }
        }
        return any ? payload : null;
    }

    /**
     * Device Flow 负载是否包含核心字段。
     *
     * @param payload 负载；可为 null
     * @return 有效为 true
     */
    public static boolean hasDeviceFlowMaterial(DreaminaDeviceLogin payload) {
        if (payload == null) {
            return false;
        }
        return DreaminaStrings.isNotBlank(payload.getDeviceCode())
            || DreaminaStrings.isNotBlank(payload.getVerificationUri())
            || DreaminaStrings.isNotBlank(payload.getUserCode());
    }

    private static Long parseLong(String value) {
        if (DreaminaStrings.isBlank(value)) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
