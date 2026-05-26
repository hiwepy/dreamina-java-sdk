package io.github.hiwepy.dreamina.cli.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.hiwepy.dreamina.util.DreaminaStrings;

/**
 * {@link DreaminaQueryQueueInfo} 映射后处理（解析内嵌 {@code debug_info}）。
 *
 * @author wandl
 * @since 1.0.0
 */
public final class DreaminaQueueInfoSupport {

    private DreaminaQueueInfoSupport() {
    }

    /**
     * 若 {@code debug_info} 为非空 JSON 字符串，则填充 {@link DreaminaQueryQueueInfo#getParsedDebugInfo()}。
     *
     * @param objectMapper Jackson 映射器；不得为 null
     * @param queueInfo    队列对象；可为 null
     */
    public static void enrichParsedDebugInfo(ObjectMapper objectMapper, DreaminaQueryQueueInfo queueInfo) {
        if (queueInfo == null || DreaminaStrings.isBlank(queueInfo.getDebugInfo())) {
            return;
        }
        try {
            DreaminaQueryQueueDebugInfo parsed =
                objectMapper.readValue(queueInfo.getDebugInfo().trim(), DreaminaQueryQueueDebugInfo.class);
            queueInfo.setParsedDebugInfo(parsed);
        } catch (Exception ignored) {
            // 保留原始 debug_info 字符串，不阻断主流程
        }
    }
}
