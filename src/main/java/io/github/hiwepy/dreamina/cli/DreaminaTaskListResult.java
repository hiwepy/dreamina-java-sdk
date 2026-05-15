package io.github.hiwepy.dreamina.cli;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Getter;

/**
 * {@code dreamina list_task}：任务数组视图。
 *
 * @author wandl
 * @since 1.0.0
 */
@Getter
@Builder
public class DreaminaTaskListResult {

    /**
     * 解析成功的 JSON 数组根。
     */
    private final JsonNode tasks;

    /**
     * {@link #tasks} 为数组时的元素个数；否则 {@code null}。
     */
    private final Integer taskCount;

    /**
     * 原始 stdout（便于审计或二次解析）。
     */
    private final String rawStdout;

    /**
     * 原始 stderr（若有）。
     */
    private final String rawStderr;
}
