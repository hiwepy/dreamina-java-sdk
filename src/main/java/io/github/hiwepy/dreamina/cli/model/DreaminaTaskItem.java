package io.github.hiwepy.dreamina.cli.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.hiwepy.dreamina.cli.model.DreaminaResultJson;
import lombok.Data;

/**
 * {@code dreamina list_task} 数组中单条任务记录。
 *
 * @author wandl
 * @since 1.0.0
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DreaminaTaskItem {

    @JsonProperty("submit_id")
    private String submitId;

    /**
     * 任务提示词（部分 {@code gen_task_type} 在 {@code list_task} 中返回）。
     */
    private String prompt;

    @JsonProperty("gen_status")
    private String genStatus;

    @JsonProperty("gen_task_type")
    private String genTaskType;

    @JsonProperty("fail_reason")
    private String failReason;

    @JsonProperty("result_json")
    private DreaminaResultJson resultJson;

    /**
     * 计费与权益信息（生产 {@code list_task} 将 {@code credit_count} 置于此对象内）。
     */
    @JsonProperty("commerce_info")
    private DreaminaCommerceInfo commerceInfo;

    /**
     * 部分 CLI 版本在任务根上的积分字段；与 {@link #commerceInfo} 互斥时以 commerce 为准。
     */
    @JsonProperty("credit_count")
    private Long creditCount;

    /**
     * 解析本任务消耗的积分：优先 {@code commerce_info.credit_count}，否则根级 {@code credit_count}。
     *
     * @return 积分或 null
     */
    public Long resolveCreditCount() {
        if (commerceInfo != null && commerceInfo.getCreditCount() != null) {
            return commerceInfo.getCreditCount();
        }
        return creditCount;
    }
}
