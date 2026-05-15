package io.github.hiwepy.dreamina.exception;

import io.github.hiwepy.dreamina.cli.DreaminaCliResult;
import lombok.Getter;

/**
 * Dreamina CLI 执行层基础异常。
 * <p>
 * 封装单次进程调用的诊断信息及可选快照，供上层决定是否重试、降级或告警；不耦合业务文案。
 * </p>
 *
 * @author wandl
 * @since 1.0.0
 */
@Getter
public class DreaminaCliException extends RuntimeException {

    /** 最近一次可观测结果（例如在已拿到输出后超时或非零退出） */
    private final DreaminaCliResult partialResult;

    /**
     * 构造 Dreamina CLI 执行异常。
     *
     * @param message        技术性说明（面向日志）
     * @param cause          原始原因
     * @param partialResult  若在失败前已形成结果则传入，否则为 null
     */
    public DreaminaCliException(String message, Throwable cause, DreaminaCliResult partialResult) {
        super(message, cause);
        this.partialResult = partialResult;
    }

    /**
     * 构造无 {@link #cause} 的执行异常。
     *
     * @param message       技术性说明（面向日志）
     * @param partialResult 部分结果快照
     */
    public DreaminaCliException(String message, DreaminaCliResult partialResult) {
        super(message);
        this.partialResult = partialResult;
    }
}
