package io.github.hiwepy.dreamina.cli;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

/**
 * {@code dreamina session list} 的结构化视图。
 *
 * @author wandl
 * @since 1.0.0
 */
@Getter
@Builder
public class DreaminaSessionListResult {

    /**
     * 尽力解析出的会话表格行。
     */
    private final List<DreaminaSessionRow> rows;

    /**
     * 合并 stdout/stderr 的全文快照（降级排障）。
     */
    private final String rawCombinedText;
}
