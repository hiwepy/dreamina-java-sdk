package io.github.hiwepy.dreamina.cli.availability;

import io.github.hiwepy.dreamina.cli.DreaminaCliResult;
import lombok.Builder;
import lombok.Getter;

/**
 * Dreamina CLI 启动/就绪探测结果（纯 SDK，无 Spring 依赖）。
 *
 * @author wandl
 * @since 1.0.0
 */
@Getter
@Builder
public class DreaminaCliAvailabilityReport {

    private final DreaminaCliAvailabilityStatus status;
    private final boolean available;
    private final String configuredExecutable;
    private final String resolvedExecutablePath;
    private final String message;
    private final DreaminaCliResult probeResult;

    /**
     * @return 是否可安全调用 {@code dreamina} 子命令
     */
    public boolean isAvailable() {
        return available;
    }

    /**
     * 构造面向日志/异常的诊断文本。
     *
     * @return 单行或多行说明
     */
    public String toDiagnosticMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("Dreamina CLI ");
        sb.append(available ? "ready" : "unavailable");
        sb.append(" [").append(status).append(']');
        if (configuredExecutable != null) {
            sb.append(" executable=").append(configuredExecutable);
        }
        if (resolvedExecutablePath != null) {
            sb.append(" resolved=").append(resolvedExecutablePath);
        }
        if (message != null && !message.isEmpty()) {
            sb.append(" — ").append(message);
        }
        return sb.toString();
    }
}
