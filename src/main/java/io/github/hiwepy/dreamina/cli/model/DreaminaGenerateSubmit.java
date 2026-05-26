package io.github.hiwepy.dreamina.cli.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.hiwepy.dreamina.cli.model.DreaminaQueryQueueInfo;
import lombok.Data;

/**
 * 异步生成类命令（{@code text2image}、{@code image2video} 等）提交后的 JSON 根负载。
 *
 * @author wandl
 * @since 1.0.0
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DreaminaGenerateSubmit {

    @JsonProperty("submit_id")
    private String submitId;

    @JsonProperty("gen_status")
    private String genStatus;

    @JsonProperty("fail_reason")
    private String failReason;

    @JsonProperty("queue_info")
    private DreaminaQueryQueueInfo queueInfo;

    /**
     * 服务端追踪 ID（生产 {@code text2image} 等提交响应均返回）。
     */
    private String logid;

    @JsonProperty("credit_count")
    private Long creditCount;
}
