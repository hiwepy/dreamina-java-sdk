package io.github.hiwepy.dreamina.cli.opts;

import lombok.Getter;

/**
 * Dreamina 常见宽高比枚举。
 *
 * @author wandl
 * @since 1.0.0
 */
@Getter
public enum DreaminaRatio {

    RATIO_21_9("21:9"),
    RATIO_16_9("16:9"),
    RATIO_3_2("3:2"),
    RATIO_4_3("4:3"),
    RATIO_1_1("1:1"),
    RATIO_3_4("3:4"),
    RATIO_2_3("2:3"),
    RATIO_9_16("9:16");

    private final String cliValue;

    DreaminaRatio(String cliValue) {
        this.cliValue = cliValue;
    }
}
