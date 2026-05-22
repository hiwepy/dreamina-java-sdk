package io.github.hiwepy.dreamina.cli.opts;

import lombok.Getter;

/**
 * 视频模型版本枚举。
 *
 * @author wandl
 * @since 1.0.0
 */
@Getter
public enum DreaminaVideoModelVersion {

    SEEDANCE_2_0_FAST("seedance2.0fast"),
    SEEDANCE_2_0("seedance2.0"),
    SEEDANCE_2_0_FAST_VIP("seedance2.0fast_vip"),
    SEEDANCE_2_0_VIP("seedance2.0_vip"),
    MODEL_3_0("3.0"),
    MODEL_3_0_FAST("3.0fast"),
    MODEL_3_0_PRO("3.0pro"),
    MODEL_3_5_PRO("3.5pro"),
    /** image2video 接受的 CLI 别名。 */
    MODEL_3_0_FAST_UNDERSCORE("3.0_fast"),
    MODEL_3_0_PRO_UNDERSCORE("3.0_pro"),
    MODEL_3_5_PRO_UNDERSCORE("3.5_pro");

    private final String cliValue;

    DreaminaVideoModelVersion(String cliValue) {
        this.cliValue = cliValue;
    }

    /**
     * 是否可用于 {@code text2video}（CLI 仅 seedance 四型号）。
     *
     * @return true 表示 seedance 系列
     */
    public boolean supportsText2Video() {
        return this == SEEDANCE_2_0
            || this == SEEDANCE_2_0_FAST
            || this == SEEDANCE_2_0_VIP
            || this == SEEDANCE_2_0_FAST_VIP;
    }

    /**
     * 按 CLI help 返回该模型允许的视频时长下限（秒）。
     */
    public int minDurationSeconds() {
        if (this == MODEL_3_0 || this == MODEL_3_0_FAST || this == MODEL_3_0_PRO
            || this == MODEL_3_0_FAST_UNDERSCORE || this == MODEL_3_0_PRO_UNDERSCORE) {
            return 3;
        }
        if (this == MODEL_3_5_PRO || this == MODEL_3_5_PRO_UNDERSCORE) {
            return 4;
        }
        return 4;
    }

    /**
     * 按 CLI help 返回该模型允许的视频时长上限（秒）。
     */
    public int maxDurationSeconds() {
        if (this == MODEL_3_0 || this == MODEL_3_0_FAST || this == MODEL_3_0_PRO
            || this == MODEL_3_0_FAST_UNDERSCORE || this == MODEL_3_0_PRO_UNDERSCORE) {
            return 10;
        }
        if (this == MODEL_3_5_PRO || this == MODEL_3_5_PRO_UNDERSCORE) {
            return 12;
        }
        return 15;
    }
}
