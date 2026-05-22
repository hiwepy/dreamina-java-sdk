package io.github.hiwepy.dreamina.cli.opts;

import lombok.Getter;

/**
 * 视频分辨率类型。
 *
 * @author wandl
 * @since 1.0.0
 */
@Getter
public enum DreaminaVideoResolutionType {

    RESOLUTION_720P("720p"),
    RESOLUTION_1080P("1080p");

    private final String cliValue;

    DreaminaVideoResolutionType(String cliValue) {
        this.cliValue = cliValue;
    }
}
