package io.github.hiwepy.dreamina.cli;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Getter;

/**
 * 各类异步生成命令提交后的统一结构化视图（{@code text2image}、{@code image2video} 等）。
 *
 * @author wandl
 * @since 1.0.0
 */
@Getter
@Builder
public class DreaminaGenerateSubmitResult {

    /**
     * 提交 ID（后续 {@link DreaminaCliExecutor#queryResult(String)} 轮询依赖此字段）。
     */
    private final String submitId;

    /**
     * 初始任务状态（多为 {@code querying}；也可能直接失败）。
     */
    private final String genStatus;

    /**
     * 失败原因（若有）。
     */
    private final String failReason;

    /**
     * 队列信息（提交响应有时不包含）。
     */
    private final JsonNode queueInfo;

    /**
     * 日志关联 ID（便于平台侧排障）。
     */
    private final String logId;

    /**
     * 额度消耗预估或记账字段（若 CLI 输出）。
     */
    private final Long creditCount;

    /**
     * 保留完整 JSON，兼容未知字段演进。
     */
    private final JsonNode json;

    /**
     * JSON 解析失败时的降级文本。
     */
    private final String rawTextFallback;
}
