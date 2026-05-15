package io.github.hiwepy.dreamina.cli;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 将 {@link DreaminaCliResult} 原始快照与命令专属的<strong>结构化负载</strong>绑定在一起。
 * <p>
 * 结构化对象为尽力解析产物（字段可为 null）；任何编排代码都应优先读取 {@link #getStructured()}，
 * 同时在契约不满足或演进场景中保留 {@link #getRaw()} 作为兜底。
 * </p>
 *
 * @param <T> 结构化负载类型（如 {@link DreaminaVersionResult}）
 * @author wandl
 * @since 1.0.0
 */
@Getter
@RequiredArgsConstructor
public final class DreaminaCliTypedResult<T> {

    /**
     * Commons Exec 层的原始聚合输出（stdout/stderr/退出码/粗略正则摘要）。
     */
    private final DreaminaCliResult raw;

    /**
     * JSON / 表格文本之上的结构化视图。
     */
    private final T structured;

    /**
     * 便捷工厂：绑定原始快照与结构化负载。
     *
     * @param raw        CLI 原始结果；不得为 null
     * @param structured 结构化负载；不得为 null（未知场景可用占位对象而非 null）
     * @param <T>        负载类型
     */
    public static <T> DreaminaCliTypedResult<T> of(DreaminaCliResult raw, T structured) {
        return new DreaminaCliTypedResult<>(raw, structured);
    }
}
