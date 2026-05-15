package io.github.hiwepy.dreamina.cli.parser;

import lombok.Builder;
import lombok.Getter;

/**
 * 对 Dreamina CLI 输出的「尽力而为」结构化解析快照。
 * <p>
 * 实际 CLI 文案或格式可能演进，解析失败时必须允许调用方降级为仅依赖 {@link DreaminaCliResult#getStdout()}
 * 与 {@link DreaminaCliResult#getStderr()} 的原始文本；本类型字段均可为空。
 * </p>
 *
 * @author wandl
 * @since 1.0.0
 */
@Getter
@Builder
public class DreaminaParsedFields {

    /**
     * 任务提交成功后可能出现的提交 ID。
     */
    private final String submitId;

    /**
     * 若能从输出中识别的用户积分／额度类数值（语义依 Dreamina CLI 而定）。
     */
    private final Long credit;

    /**
     * 是否需要后续轮询（尽力从输出中识别的布尔提示；无法识别时为 null）。
     */
    private final Boolean pollRecommended;
}
