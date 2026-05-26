package io.github.hiwepy.dreamina.cli.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * OAuth Device Flow 材料（JSON 或 {@code relogin} 等命令的键值对文本）。
 *
 * @author wandl
 * @since 1.0.0
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DreaminaDeviceLogin {

    @JsonProperty("device_code")
    @JsonAlias("deviceCode")
    private String deviceCode;

    @JsonProperty("verification_uri")
    @JsonAlias("verificationUri")
    private String verificationUri;

    @JsonProperty("user_code")
    @JsonAlias("userCode")
    private String userCode;

    /**
     * 轮询间隔（如 {@code 1s}）；文本输出常见。
     */
    @JsonProperty("poll_interval")
    private String pollInterval;

    /**
     * 设备码过期时间（ISO-8601 字符串）。
     */
    @JsonProperty("expires_at")
    private String expiresAt;

    /**
     * 是否包含可用于 {@code checklogin} 的核心 Device Flow 字段。
     *
     * @return 有效为 true
     */
    public boolean isMaterialPresent() {
        return io.github.hiwepy.dreamina.cli.parser.DreaminaLoginTextParser.hasDeviceFlowMaterial(this);
    }
}
