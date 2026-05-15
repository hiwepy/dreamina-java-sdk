package io.github.hiwepy.dreamina.cli;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Getter;

/**
 * {@code dreamina user_credit} 的结构化视图。
 *
 * @author wandl
 * @since 1.0.0
 */
@Getter
@Builder
public class DreaminaUserCreditResult {

    /**
     * 账户剩余额度（字段名依官方 JSON：{@code total_credit}）。
     */
    private final Long totalCredit;

    /**
     * 用户数字 ID（可选）。
     */
    private final Long userId;

    /**
     * 用户展示名（可选，可能为空字符串）。
     */
    private final String userName;

    /**
     * VIP 档位（可选）。
     */
    private final String vipLevel;

    /**
     * 解析成功的 JSON 根节点。
     */
    private final JsonNode json;

    /**
     * JSON 缺失时的降级全文。
     */
    private final String rawTextFallback;
}
