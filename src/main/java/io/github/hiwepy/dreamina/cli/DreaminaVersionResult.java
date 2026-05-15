package io.github.hiwepy.dreamina.cli;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Getter;

/**
 * {@code dreamina version} 的结构化视图。
 *
 * @author wandl
 * @since 1.0.0
 */
@Getter
@Builder
public class DreaminaVersionResult {

    /**
     * CLI 自报告的版本标识（通常等价于带后缀的构建号）。
     */
    private final String version;

    /**
     * 源码提交哈希（不带 dirty 后缀时的语义依 CLI 而定）。
     */
    private final String commit;

    /**
     * 构建时间 ISO 字符串。
     */
    private final String buildTime;

    /**
     * 解析成功的 JSON 根节点；无法解析 JSON 时为 {@code null}。
     */
    private final JsonNode json;

    /**
     * 当 JSON 不稳定时的降级全文（通常为 stdout）。
     */
    private final String rawTextFallback;
}
