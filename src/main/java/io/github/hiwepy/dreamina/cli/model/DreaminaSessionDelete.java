package io.github.hiwepy.dreamina.cli.model;

import lombok.Builder;
import lombok.Getter;

/**
 * {@code dreamina session delete/rm} 解析体（成功时 CLI 常输出 {@code deleted}）。
 *
 * @author wandl
 * @since 1.0.0
 */
@Getter
@Builder
public class DreaminaSessionDelete {

    private final boolean deleted;
}
