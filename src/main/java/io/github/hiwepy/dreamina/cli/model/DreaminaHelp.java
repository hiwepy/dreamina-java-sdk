package io.github.hiwepy.dreamina.cli.model;

import lombok.Builder;
import lombok.Getter;

/**
 * {@code dreamina help} 解析体（主题；全文见 {@link io.github.hiwepy.dreamina.cli.DreaminaCliResponse#getStdout()}）。
 *
 * @author wandl
 * @since 1.0.0
 */
@Getter
@Builder
public class DreaminaHelp {

    private final String topic;
}
