package io.github.hiwepy.dreamina.cli.model;

import lombok.Builder;
import lombok.Getter;

/**
 * {@code dreamina logout} 解析体。
 *
 * @author wandl
 * @since 1.0.0
 */
@Getter
@Builder
public class DreaminaLogout {

    private final Boolean localSessionCleared;
}
