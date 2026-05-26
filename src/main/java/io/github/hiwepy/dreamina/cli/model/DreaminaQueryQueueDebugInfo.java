package io.github.hiwepy.dreamina.cli.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * {@code queue_info.debug_info} 内嵌 JSON 字符串解析后的诊断结构。
 * <p>
 * 该字段由 SDK 在映射阶段解析得到，并非 CLI 顶层独立键。
 * </p>
 *
 * @author wandl
 * @since 1.0.0
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DreaminaQueryQueueDebugInfo {

    @JsonProperty("have_no_dreamina_queue_name")
    private Boolean haveNoDreaminaQueueName;

    @JsonProperty("dreamina_matrix_queue_name")
    private String dreaminaMatrixQueueName;

    @JsonProperty("dreamina_matrix_req_key")
    private String dreaminaMatrixReqKey;

    @JsonProperty("dreamina_matrix_second_req_key")
    private String dreaminaMatrixSecondReqKey;

    @JsonProperty("have_no_queue_name")
    private Boolean haveNoQueueName;

    @JsonProperty("queue_name")
    private String queueName;

    @JsonProperty("matrix_req_key")
    private String matrixReqKey;

    @JsonProperty("matrix_second_req_key")
    private String matrixSecondReqKey;
}
