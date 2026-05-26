package io.github.hiwepy.dreamina.cli.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * {@code query_result} / 部分生成提交响应中的 {@code queue_info} 对象。
 *
 * @author wandl
 * @since 1.0.0
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DreaminaQueryQueueInfo {

    /**
     * 队列中的序号。
     */
    @JsonProperty("queue_idx")
    private Integer queueIdx;

    /**
     * 任务优先级。
     */
    private Integer priority;

    /**
     * 队列状态（例如 {@code Finish}、{@code Waiting} 等，语义依官方）。
     */
    @JsonProperty("queue_status")
    private String queueStatus;

    /**
     * 当前队列长度。
     */
    @JsonProperty("queue_length")
    private Integer queueLength;

    /**
     * 原始调试 JSON 字符串（CLI 原样输出）。
     */
    @JsonProperty("debug_info")
    private String debugInfo;

    /**
     * 由 SDK 解析 {@link #debugInfo} 得到的结构化视图；解析失败或未提供时为 {@code null}。
     */
    private DreaminaQueryQueueDebugInfo parsedDebugInfo;
}
