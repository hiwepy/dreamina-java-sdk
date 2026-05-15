package io.github.hiwepy.dreamina.cli.opts;

import lombok.Getter;

/**
 * 图像模型版本枚举。
 * <p>
 * 同时覆盖文生图与图生图场景；图生图是否允许某一版本由请求对象额外约束。
 * </p>
 *
 * @author wandl
 * @since 1.0.0
 */
@Getter
public enum DreaminaImageModelVersion {

    MODEL_3_0("3.0"),
    MODEL_3_1("3.1"),
    MODEL_4_0("4.0"),
    MODEL_4_1("4.1"),
    MODEL_4_5("4.5"),
    MODEL_4_6("4.6"),
    MODEL_5_0("5.0");

    private final String cliValue;

    DreaminaImageModelVersion(String cliValue) {
        this.cliValue = cliValue;
    }

    /**
     * 是否满足图生图最低版本要求（4.0+）。
     *
     * @return true 表示可用于 image2image
     */
    public boolean supportsImageToImage() {
        return this.ordinal() >= MODEL_4_0.ordinal();
    }
}
