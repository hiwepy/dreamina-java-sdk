package io.github.hiwepy.dreamina.cli.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Collections;
import java.util.List;
import lombok.Data;

/**
 * {@code query_result} 响应中的 {@code result_json} 对象。
 *
 * @author wandl
 * @since 1.0.0
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DreaminaResultJson {

    /**
     * 生成成功的图像列表；缺失时为 {@code null}（调用方可通过 {@link #safeImages()} 得到空列表）。
     */
    private List<DreaminaQueryImage> images;

    /**
     * 生成成功的视频列表；缺失时为 {@code null}。
     */
    private List<DreaminaQueryVideo> videos;

    /**
     * 返回非 null 的图像列表视图。
     *
     * @return 图像列表，永不为 null
     */
    public List<DreaminaQueryImage> safeImages() {
        return images == null ? Collections.emptyList() : images;
    }

    /**
     * 返回非 null 的视频列表视图。
     *
     * @return 视频列表，永不为 null
     */
    public List<DreaminaQueryVideo> safeVideos() {
        return videos == null ? Collections.emptyList() : videos;
    }
}
