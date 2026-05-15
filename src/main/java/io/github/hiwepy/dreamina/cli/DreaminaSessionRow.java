package io.github.hiwepy.dreamina.cli;

import lombok.Builder;
import lombok.Getter;

/**
 * {@code dreamina session list} 表格中的一行摘要。
 *
 * @author wandl
 * @since 1.0.0
 */
@Getter
@Builder
public class DreaminaSessionRow {

    /**
     * 会话数字 ID。
     */
    private final String id;

    /**
     * 会话展示名称。
     */
    private final String name;

    /**
     * 是否置顶（Yes/No，文本级别快照）。
     */
    private final String pinned;

    /**
     * 最近更新时间（CLI 原文格式）。
     */
    private final String updatedAt;
}
