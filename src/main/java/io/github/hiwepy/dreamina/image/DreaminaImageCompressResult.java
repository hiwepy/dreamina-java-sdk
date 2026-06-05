package io.github.hiwepy.dreamina.image;

import lombok.Builder;
import lombok.Value;

/**
 * 图片压缩结果摘要。
 *
 * @author wandl
 * @since 1.0.0
 */
@Value
@Builder
public class DreaminaImageCompressResult {

    /** 压缩后的图片字节。 */
    byte[] bytes;

    /** 是否实际执行了压缩（开关开启且输出与输入不同或尺寸/质量已调整）。 */
    boolean applied;

    /** 压缩前字节长度。 */
    int originalSize;

    /** 压缩后字节长度。 */
    int compressedSize;

    /** 实际使用的缩放比例。 */
    double scale;

    /** 实际使用的输出质量（1–100）。 */
    int quality;

    /** 输出 ImageIO 格式名（如 jpg、png）。 */
    String outputFormat;
}
