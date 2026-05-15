package io.github.hiwepy.dreamina.cli;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

/**
 * {@code dreamina session search} 的结构化视图。
 *
 * @author wandl
 * @since 1.0.0
 */
@Getter
@Builder
public class DreaminaSessionSearchResult {

    /**
     * 搜索关键字摘要（来自调用参数快照）。
     */
    private final String queryTerm;

    /**
     * 命中会话表格（通常为精简列）。
     */
    private final List<DreaminaSessionRow> matches;

    /**
     * 原始 CLI 文本快照。
     */
    private final String rawCombinedText;
}
