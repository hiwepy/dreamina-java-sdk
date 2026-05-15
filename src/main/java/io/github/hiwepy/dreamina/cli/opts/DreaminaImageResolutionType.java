package io.github.hiwepy.dreamina.cli.opts;

import lombok.Getter;

/**
 * 图像分辨率类型。
 *
 * @author wandl
 * @since 1.0.0
 */
@Getter
public enum DreaminaImageResolutionType {

    RESOLUTION_1K("1k"),
    RESOLUTION_2K("2k"),
    RESOLUTION_4K("4k");

    private final String cliValue;

    DreaminaImageResolutionType(String cliValue) {
        this.cliValue = cliValue;
    }
}
