package io.github.hiwepy.dreamina.cli;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.hiwepy.dreamina.cli.parser.DreaminaParsedFields;
import io.github.hiwepy.dreamina.util.DreaminaStrings;
import lombok.Builder;
import lombok.Getter;

/**
 * 单次 CLI 命令的<strong>所见即所得</strong>结果：原始输出 + 解析后的业务对象。
 * <p>
 * {@link #getBody()} 对应该命令 stdout 的结构化视图（JSON 反序列化或文本/表格解析）；
 * 解析失败时为 {@code null}，此时仍可通过 {@link #getStdout()} / {@link #getStderr()} 读取原文。
 * </p>
 *
 * @param <T> 本命令解析体类型（如 {@link io.github.hiwepy.dreamina.cli.model.DreaminaVersion}）
 * @author wandl
 * @since 1.0.0
 */
@Getter
@Builder
public final class DreaminaCliResponse<T> {

    private final String stdout;
    private final String stderr;
    private final Integer exitCode;
    private final boolean success;
    private final DreaminaParsedFields parsed;
    private final T body;
    private final JsonNode json;

    /**
     * 绑定原始快照与解析体（无 JSON 树）。
     *
     * @param raw  CLI 原始结果
     * @param body 解析体，可为 null
     */
    public static <T> DreaminaCliResponse<T> of(DreaminaCliResult raw, T body) {
        return of(raw, body, null);
    }

    /**
     * 绑定原始快照、解析体与 JSON 根节点。
     *
     * @param raw  CLI 原始结果
     * @param body 解析体，可为 null
     * @param json JSON 根；非 JSON 命令可为 null
     */
    public static <T> DreaminaCliResponse<T> of(DreaminaCliResult raw, T body, JsonNode json) {
        return DreaminaCliResponse.<T>builder()
            .stdout(raw.getStdout())
            .stderr(raw.getStderr())
            .exitCode(raw.getExitCode())
            .success(raw.isSuccess())
            .parsed(raw.getParsed())
            .body(body)
            .json(json)
            .build();
    }

    /**
     * @return 合并 stdout + stderr（与历史 {@code combinedText} 一致）
     */
    public String getCombinedText() {
        String out = stdout == null ? "" : stdout;
        String err = stderr == null ? "" : stderr;
        if (DreaminaStrings.isBlank(err)) {
            return out;
        }
        if (DreaminaStrings.isBlank(out)) {
            return err;
        }
        return out + System.lineSeparator() + err;
    }

    /**
     * @return 是否解析出非空 body
     */
    public boolean hasBody() {
        return body != null;
    }
}
