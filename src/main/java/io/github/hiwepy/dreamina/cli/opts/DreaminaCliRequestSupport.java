package io.github.hiwepy.dreamina.cli.opts;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Dreamina CLI 请求对象公共校验与参数拼装支持。
 *
 * @author wandl
 * @since 1.0.0
 */
public final class DreaminaCliRequestSupport {

    private DreaminaCliRequestSupport() {
    }

    /**
     * 断言文本参数非空白。
     *
     * @param value 参数值
     * @param label 参数名
     * @return 去除首尾空白后的值
     */
    public static String requireNonBlank(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value.trim();
    }

    /**
     * 校验数值范围。
     *
     * @param value 当前值
     * @param min   最小值
     * @param max   最大值
     * @param label 参数名
     */
    public static void requireRange(Integer value, int min, int max, String label) {
        if (value == null) {
            return;
        }
        if (value < min || value > max) {
            throw new IllegalArgumentException(label + " must be in range [" + min + ", " + max + "]");
        }
    }

    /**
     * 校验非负数值。
     *
     * @param value 参数值
     * @param label 参数名
     */
    public static void requireNonNegative(Integer value, String label) {
        if (value == null) {
            return;
        }
        if (value < 0) {
            throw new IllegalArgumentException(label + " must be non-negative");
        }
    }

    /**
     * 校验会话 ID。
     *
     * @param sessionId 会话 ID
     */
    public static void requireSessionId(Long sessionId) {
        if (sessionId == null) {
            return;
        }
        if (sessionId < 0) {
            throw new IllegalArgumentException("sessionId must be non-negative");
        }
    }

    /**
     * 校验本地文件存在且可读。
     *
     * @param rawPath 文件路径
     * @param label   参数名
     * @return 规范化后的路径文本
     */
    public static String requireReadableFile(String rawPath, String label) {
        String path = requireNonBlank(rawPath, label);
        Path resolved = Path.of(path);
        if (!Files.exists(resolved)) {
            throw new IllegalArgumentException(label + " does not exist: " + path);
        }
        if (!Files.isRegularFile(resolved)) {
            throw new IllegalArgumentException(label + " is not a file: " + path);
        }
        if (!Files.isReadable(resolved)) {
            throw new IllegalArgumentException(label + " is not readable: " + path);
        }
        return path;
    }

    /**
     * 校验文件列表数量与可读性。
     *
     * @param rawPaths 文件路径列表
     * @param label    参数名
     * @param minCount 最小数量
     * @param maxCount 最大数量
     * @return 清洗后的路径列表
     */
    public static List<String> requireReadableFiles(List<String> rawPaths, String label, int minCount, int maxCount) {
        if (rawPaths == null || rawPaths.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be empty");
        }
        if (rawPaths.size() < minCount || rawPaths.size() > maxCount) {
            throw new IllegalArgumentException(label + " size must be in range [" + minCount + ", " + maxCount + "]");
        }
        List<String> cleaned = new ArrayList<>(rawPaths.size());
        for (int i = 0; i < rawPaths.size(); i++) {
            cleaned.add(requireReadableFile(rawPaths.get(i), label + "[" + i + "]"));
        }
        return cleaned;
    }

    /**
     * 逗号拼接文件列表，适配 `--images=a,b,c` 这类 Dreamina CLI 语法。
     *
     * @param paths 已校验路径列表
     * @return CSV 字符串
     */
    public static String csv(List<String> paths) {
        return String.join(",", paths);
    }

    /**
     * 追加 `--key=value` 形式参数。
     *
     * @param args  参数集合
     * @param key   参数名（含 `--`）
     * @param value 参数值
     */
    public static void addFlag(List<String> args, String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        args.add(key + "=" + value.trim());
    }

    /**
     * 追加整数型 flag。
     *
     * @param args  参数集合
     * @param key   参数名
     * @param value 参数值
     */
    public static void addFlag(List<String> args, String key, Integer value) {
        if (value == null) {
            return;
        }
        args.add(key + "=" + value);
    }

    /**
     * 追加长整型 flag。
     *
     * @param args  参数集合
     * @param key   参数名
     * @param value 参数值
     */
    public static void addFlag(List<String> args, String key, Long value) {
        if (value == null) {
            return;
        }
        args.add(key + "=" + value);
    }

    /**
     * 追加调用方自定义原生参数。
     *
     * @param args             目标参数集合
     * @param additionalRawArgs 原生参数
     */
    public static void addAdditionalArgs(List<String> args, List<String> additionalRawArgs) {
        if (additionalRawArgs == null || additionalRawArgs.isEmpty()) {
            return;
        }
        for (String arg : additionalRawArgs) {
            if (arg != null && !arg.isBlank()) {
                args.add(arg.trim());
            }
        }
    }

    /**
     * 复制调用方自定义参数，避免外部列表被后续修改影响。
     *
     * @param additionalRawArgs 原生参数
     * @return 不可变视图
     */
    public static List<String> copyAdditionalArgs(List<String> additionalRawArgs) {
        if (additionalRawArgs == null || additionalRawArgs.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> copied = new ArrayList<>();
        addAdditionalArgs(copied, additionalRawArgs);
        return Collections.unmodifiableList(copied);
    }
}
