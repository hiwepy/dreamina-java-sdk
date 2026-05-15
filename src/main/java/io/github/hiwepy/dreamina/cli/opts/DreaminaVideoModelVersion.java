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
    MODEL_3_5_PRO("3.5pro");

    private final String cliValue;

    DreaminaVideoModelVersion(String cliValue) {
        this.cliValue = cliValue;
    }
}
