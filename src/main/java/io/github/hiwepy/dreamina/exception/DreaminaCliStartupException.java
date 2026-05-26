package io.github.hiwepy.dreamina.exception;

import io.github.hiwepy.dreamina.cli.availability.DreaminaCliAvailabilityReport;
import lombok.Getter;

/**
 * 应用启动阶段 Dreamina CLI 不可用且配置为 fail-fast 时抛出。
 *
 * @author wandl
 * @since 1.0.0
 */
@Getter
public class DreaminaCliStartupException extends DreaminaCliException {

    private final DreaminaCliAvailabilityReport availabilityReport;

    /**
     * @param message  诊断说明
     * @param report   探测报告
     */
    public DreaminaCliStartupException(String message, DreaminaCliAvailabilityReport report) {
        super(message, report != null ? report.getProbeResult() : null);
        this.availabilityReport = report;
    }
}
