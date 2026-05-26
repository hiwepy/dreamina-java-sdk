package io.github.hiwepy.dreamina.cli.model;

import lombok.Builder;
import lombok.Getter;

/**
 * {@code dreamina session create/rename} 解析体。
 *
 * @author wandl
 * @since 1.0.0
 */
@Getter
@Builder
public class DreaminaSessionMutation {

    public enum Kind {
        CREATE,
        RENAME,
        UNKNOWN
    }

    private final Kind kind;
    private final String sessionId;
    private final String sessionName;
}
