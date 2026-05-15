package io.github.hiwepy.dreamina.cli;

import lombok.Builder;
import lombok.Getter;

/**
 * {@code dreamina session create}/{@code rename} 等变更类子命令的结构化摘要。
 *
 * @author wandl
 * @since 1.0.0
 */
@Getter
@Builder
public class DreaminaSessionMutationResult {

    /**
     * 推断出的变更类型。
     */
    private final Kind kind;

    /**
     * 受影响会话 ID（字符串以避免溢出）。
     */
    private final String sessionId;

    /**
     * 会话名称（创建 / 重命名后）。
     */
    private final String sessionName;

    /**
     * CLI 返回的单行或多行原文。
     */
    private final String messageLine;

    /**
     * 变更类别枚举。
     */
    public enum Kind {
        /** 新建会话 */
        CREATE,
        /** 修改名称 */
        RENAME,
        /** 未能可靠归类 */
        UNKNOWN
    }
}
