package io.github.hiwepy.dreamina.cli.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * {@code result_json.videos[]} 单条视频产物。
 *
 * @author wandl
 * @since 1.0.0
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DreaminaQueryVideo {

    /**
     * 可下载的签名视频 URL。
     */
    @JsonProperty("video_url")
    private String videoUrl;

    /**
     * 封面图 URL（若 CLI 提供）。
     */
    @JsonProperty("cover_url")
    private String coverUrl;

    /**
     * 视频宽度（像素）。
     */
    private Integer width;

    /**
     * 视频高度（像素）。
     */
    private Integer height;

    /**
     * 帧率（生产 {@code multiframe2video} 成功样例为 24）。
     */
    private Integer fps;

    /**
     * 容器格式（例如 {@code mp4}）。
     */
    private String format;

    /**
     * 时长（秒，可为小数；生产样例如 {@code 3.208}）。
     */
    private Double duration;
}
