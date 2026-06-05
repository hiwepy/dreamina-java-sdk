package io.github.hiwepy.dreamina.image;

import lombok.Builder;
import lombok.Value;

/**
 * Dreamina 生图结果压缩参数（纯 POJO，无 Spring 耦合）。
 *
 * <p>与 playwright-spring-boot-starter 中 Thumbnailator 用法对齐：
 * {@code scale} 控制等比例缩放，{@code quality} 控制有损编码质量。</p>
 *
 * @author wandl
 * @since 1.0.0
 */
@Value
@Builder
public class DreaminaImageCompressOptions {

    /**
     * 是否启用压缩；为 false 时 {@link DreaminaImageCompressSupport} 原样返回输入字节。
     */
    @Builder.Default
    boolean enabled = false;

    /**
     * 等比例缩放比例：{@code (0,1]} 缩小，{@code 1} 仅质量压缩不改变尺寸，{@code >1} 放大。
     */
    @Builder.Default
    double scale = 1.0d;

    /**
     * 输出质量（1–100），映射为 Thumbnailator {@code outputQuality(quality/100f)}。
     */
    @Builder.Default
    int quality = 85;

    /**
     * 从业务配置构造压缩选项。
     *
     * @param enabled 压缩开关
     * @param scale   缩放比例
     * @param quality 输出质量 1–100
     * @return 压缩选项
     */
    public static DreaminaImageCompressOptions of(boolean enabled, double scale, int quality) {
        return DreaminaImageCompressOptions.builder()
                .enabled(enabled)
                .scale(scale)
                .quality(quality)
                .build();
    }
}
