package io.github.hiwepy.dreamina.cli;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Getter;

/**
 * {@code dreamina query_result} 的结构化视图。
 *
 * @author wandl
 * @since 1.0.0
 */
@Getter
@Builder
public class DreaminaQueryResult {

    /**
     * 任务提交 ID。
     */
    private final String submitId;

    /**
     * 生成管线状态（成功 / 查询中 / 失败等，语义依官方）。
     */
    private final String genStatus;

    /**
     * 失败原因文本（成功或进行中通常为空字符串）。
     */
    private final String failReason;

    /**
     * 队列诊断信息（JSON 对象）；缺失时为 {@code null}。
     */
    private final JsonNode queueInfo;

    /**
     * 任务产物摘要（图像 URL / 视频等）；缺失时为 {@code null}。
     */
    private final JsonNode resultJson;

    /**
     * 本次查询关联的积分消耗提示（若 CLI 提供）。
     */
    private final Long creditCount;

    /**
     * 完整 JSON 根节点（包含未知扩展字段）。
     */
    private final JsonNode json;

    /**
     * JSON 不可解析时的降级全文。
     */
    private final String rawTextFallback;
}
