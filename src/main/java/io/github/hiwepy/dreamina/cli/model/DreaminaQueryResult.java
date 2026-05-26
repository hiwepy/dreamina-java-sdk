package io.github.hiwepy.dreamina.cli.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.hiwepy.dreamina.cli.model.DreaminaQueryQueueInfo;
import io.github.hiwepy.dreamina.cli.model.DreaminaResultJson;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lombok.Data;

/**
 * {@code dreamina query_result} 解析体（与 CLI JSON 一一对应）。
 *
 * @author wandl
 * @since 1.0.0
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DreaminaQueryResult {

    @JsonProperty("submit_id")
    private String submitId;

    /**
     * 生成提示词（{@code query_result} 进行中/部分任务类型会返回）。
     */
    private String prompt;

    /**
     * 服务端追踪 ID（与提交响应 {@code logid} 一致）。
     */
    private String logid;

    @JsonProperty("gen_status")
    private String genStatus;

    @JsonProperty("fail_reason")
    private String failReason;

    @JsonProperty("result_json")
    private DreaminaResultJson resultJson;

    @JsonProperty("queue_info")
    private DreaminaQueryQueueInfo queueInfo;

    @JsonProperty("credit_count")
    private Long creditCount;

    /**
     * @return 是否 success
     */
    public boolean isGenSuccess() {
        return genStatus != null && "success".equalsIgnoreCase(genStatus.trim());
    }

    /**
     * @return 是否 querying（任务仍在队列/生成中）
     */
    public boolean isGenQuerying() {
        return genStatus != null && "querying".equalsIgnoreCase(genStatus.trim());
    }

    /**
     * @return 图像列表，永不为 null
     */
    public List<DreaminaQueryImage> images() {
        return resultJson == null ? Collections.emptyList() : resultJson.safeImages();
    }

    /**
     * @return 视频列表，永不为 null
     */
    public List<DreaminaQueryVideo> videos() {
        return resultJson == null ? Collections.emptyList() : resultJson.safeVideos();
    }

    /**
     * @return 首张 image_url 或 null
     */
    public String firstImageUrl() {
        return images().stream()
            .map(DreaminaQueryImage::getImageUrl)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
    }

    /**
     * @return 首个 video_url 或 null
     */
    public String firstVideoUrl() {
        return videos().stream()
            .map(DreaminaQueryVideo::getVideoUrl)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
    }

    /**
     * @return 队列是否 Finish
     */
    public boolean isQueueFinished() {
        if (queueInfo == null || queueInfo.getQueueStatus() == null) {
            return false;
        }
        return "finish".equals(queueInfo.getQueueStatus().trim().toLowerCase(Locale.ROOT));
    }
}
