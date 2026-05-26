package io.github.hiwepy.dreamina.cli.model;

import lombok.Data;

/**
 * {@code dreamina login} 复用本地 OAuth 时输出的账户摘要（键值对文本，非 JSON）。
 * <p>
 * 典型 CLI 片段：
 * </p>
 * <pre>
 * 已复用当前本地 OAuth 登录态。
 * 当前登录账户信息：
 * user_id: 1552973852847448
 * vip_level: maestro
 * total_credit: 4391
 * </pre>
 *
 * @author wandl
 * @since 1.0.0
 */
@Data
public class DreaminaLoginAccount {

    /**
     * 当前登录用户数字 ID。
     */
    private Long userId;

    /**
     * VIP 档位（如 {@code maestro}）。
     */
    private String vipLevel;

    /**
     * 剩余积分。
     */
    private Long totalCredit;
}
