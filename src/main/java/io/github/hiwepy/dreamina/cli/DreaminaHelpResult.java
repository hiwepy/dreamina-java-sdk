package io.github.hiwepy.dreamina.cli;

import lombok.Builder;
import lombok.Getter;

/**
 * {@code dreamina help} / {@code dreamina help <subcommand>} 的结构化视图。
 * <p>
 * 绝大多数情况下 CLI 输出为可读文本而非 JSON，因此本类型以全文捕获为主。
 * </p>
 *
 * @author wandl
 * @since 1.0.0
 */
@Getter
@Builder
public class DreaminaHelpResult {

    /**
     * 询问的一级子命令；根帮助时为 {@code null}。
     */
    private final String topic;

    /**
     * 合并 stdout/stderr 后的帮助正文。
     */
    private final String fullText;
}
