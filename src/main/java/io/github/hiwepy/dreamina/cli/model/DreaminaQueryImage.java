package io.github.hiwepy.dreamina.cli.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * {@code result_json.images[]} 单条图像产物。
 *
 * @author wandl
 * @since 1.0.0
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DreaminaQueryImage {

    /**
     * 可下载的签名图像 URL。
     */
    @JsonProperty("image_url")
    private String imageUrl;

    /**
     * 图像宽度（像素）；CLI 未返回时为 {@code null}。
     */
    private Integer width;

    /**
     * 图像高度（像素）；CLI 未返回时为 {@code null}。
     */
    private Integer height;
}
