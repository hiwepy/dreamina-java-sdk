package io.github.hiwepy.dreamina.exception;

import io.github.hiwepy.dreamina.cli.DreaminaCliResult;
/**
 * 无法在操作系统层面启动 CLI（例如命令不存在或路径非法）时抛出。
 *
 * @author wandl
 * @since 1.0.0
 */
public class DreaminaCliExecutableFailureException extends DreaminaCliException {

    /**
     * @param message 面向日志的失败说明
     * @param cause   通常为 {@link java.io.IOException}
     */
    public DreaminaCliExecutableFailureException(String message, Throwable cause) {
        super(message, cause, null);
    }
}
