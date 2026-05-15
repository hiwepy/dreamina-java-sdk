package io.github.hiwepy.dreamina.util;

/**
 * 字符串工具（Java 8 兼容），用于替代 {@link String#isBlank()} 等 JDK 9+ API。
 */
public final class DreaminaStrings {

    private DreaminaStrings() {
    }

    /**
     * 判断是否为 null、空串或仅空白字符（与 {@code String.isBlank()} 语义一致，适用于 Java 8）。
     *
     * @param value 待检测字符串，可为 null
     * @return 无有效内容时返回 true
     */
    public static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * 与 {@link #isBlank(String)} 相反。
     *
     * @param value 待检测字符串，可为 null
     * @return 包含非空白字符时返回 true
     */
    public static boolean isNotBlank(String value) {
        return !isBlank(value);
    }
}
