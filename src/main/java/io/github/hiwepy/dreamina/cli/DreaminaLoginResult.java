package io.github.hiwepy.dreamina.cli;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Getter;

/**
 * {@code dreamina login} 系列命令的结构化视图。
 * <p>
 * 典型分支：
 * </p>
 * <ul>
 *   <li>已存在有效本地 OAuth：CLI 可能仅打印一行提示（中文或英文），{@link #isOAuthSessionReused()} 置为 true。</li>
 *   <li>{@code --headless} 首次登录：stdout 可能是 JSON，映射到 {@link #getDevice()}。</li>
 * </ul>
 *
 * @author wandl
 * @since 1.0.0
 */
@Getter
@Builder
public class DreaminaLoginResult {

    /**
     * CLI 合并文本快照。
     */
    private final String combinedText;

    /**
     * 若检测到「复用本地登录态」类语义则为 true；未知则为 {@code null}。
     */
    private final Boolean oauthSessionReused;

    /**
     * Device Flow JSON（若解析成功）。
     */
    private final DreaminaDeviceLoginResult device;

    /**
     * 任意 JSON 根节点（不一定存在）。
     */
    private final JsonNode json;
}
