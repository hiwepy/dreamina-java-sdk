package io.github.hiwepy.dreamina.cli;

import io.github.hiwepy.dreamina.cli.DreaminaCliExecutor;
import io.github.hiwepy.dreamina.cli.parser.DreaminaParsedFields;
import lombok.Builder;
import lombok.Getter;

/**
 * 一次 Dreamina CLI 调用的标准结果载体。
 * <p>
 * 始终保留原始 stdout / stderr，并附带退出码及成功标记；结构化字段通过 {@link #getParsed()} 按需访问。
 * 当底层已判定为非零退出、超时或可执行文件不可用时，由异常模型携带最近一次快照，
 * {@link DreaminaCliExecutor} 在正常返回路径下保证 {@link #success} 为 true。
 * </p>
 *
 * @author wandl
 * @since 1.0.0
 */
@Getter
@Builder
public class DreaminaCliResult {

    /**
     * 标准输出全文。
     */
    private final String stdout;

    /**
     * 标准错误全文。
     */
    private final String stderr;

    /**
     * 进程退出码；若进程未能正常产生退出码则可能为 {@code null}。
     */
    private final Integer exitCode;

    /**
     * 是否与当前执行层契约一致地表示成功。
     */
    private final boolean success;

    /**
     * 尽力解析的结构化摘要。
     */
    private final DreaminaParsedFields parsed;
}
