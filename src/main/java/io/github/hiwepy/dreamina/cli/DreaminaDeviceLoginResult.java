package io.github.hiwepy.dreamina.cli;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Getter;

/**
 * OAuth Device Flow 材料的结构化视图（来自 JSON 场景）。
 *
 * @author wandl
 * @since 1.0.0
 */
@Getter
@Builder
public class DreaminaDeviceLoginResult {

    /**
     * device_code（Device Flow）。
     */
    private final String deviceCode;

    /**
     * 用户在浏览器打开的校验 URI。
     */
    private final String verificationUri;

    /**
     * 展示给用户的短码。
     */
    private final String userCode;

    /**
     * 解析成功的 JSON 片段。
     */
    private final JsonNode json;
}
