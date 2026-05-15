package io.github.hiwepy.dreamina.cli.parser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link DreaminaCliOutputParser} 单元测试：覆盖真实 CLI（如 user_credit）常见 JSON 与文本降级格式。
 *
 * @author wandl
 * @since 1.0.0
 */
class DreaminaCliOutputParserTest {

    /**
     * 与当前 {@code dreamina user_credit} JSON 输出对齐：{@code total_credit} 带引号键名。
     */
    @Test
    void parseBestEffort_shouldReadTotalCreditFromUserCreditJson() {
        String stdout = """
            {
              "total_credit": 5877,
              "user_id": 1552973852847448,
              "user_name": "",
              "vip_level": "maestro"
            }
            """;
        DreaminaParsedFields f = DreaminaCliOutputParser.parseBestEffort(stdout, "");
        assertEquals(5877L, f.getCredit());
    }

    /**
     * 单行 JSON 也应识别额度。
     */
    @Test
    void parseBestEffort_shouldReadTotalCreditFromCompactJson() {
        DreaminaParsedFields f =
            DreaminaCliOutputParser.parseBestEffort("{\"total_credit\":42,\"user_id\":1}", "");
        assertEquals(42L, f.getCredit());
    }

    /**
     * 回退：旧式 {@code user credits: N} 文案。
     */
    @Test
    void parseBestEffort_shouldFallbackToKeyValueCreditText() {
        DreaminaParsedFields f =
            DreaminaCliOutputParser.parseBestEffort("User credits: 100\n", "");
        assertEquals(100L, f.getCredit());
    }

    /**
     * 无可识别额度时不得伪造数值。
     */
    @Test
    void parseBestEffort_shouldLeaveCreditNullWhenAbsent() {
        DreaminaParsedFields f = DreaminaCliOutputParser.parseBestEffort("{ \"ok\": true }", "");
        assertNull(f.getCredit());
    }

    /**
     * 与 {@code dreamina text2image} / {@code query_result} 等返回的多行 JSON 对齐：{@code "submit_id"} 带引号值。
     */
    @Test
    void parseBestEffort_shouldReadSubmitIdFromQuotedJson() {
        String stdout = """
            {
              "submit_id": "2fcc4089-f0d3-479c-a42f-f73c838cc626",
              "gen_status": "querying"
            }
            """;
        DreaminaParsedFields f = DreaminaCliOutputParser.parseBestEffort(stdout, "");
        assertEquals("2fcc4089-f0d3-479c-a42f-f73c838cc626", f.getSubmitId());
    }
}
