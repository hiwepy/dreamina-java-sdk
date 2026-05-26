package io.github.hiwepy.dreamina.cli.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * {@code dreamina user_credit} JSON 负载。
 *
 * @author wandl
 * @since 1.0.0
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DreaminaUserCredit {

    @JsonProperty("total_credit")
    private Long totalCredit;

    @JsonProperty("user_id")
    private Long userId;

    @JsonProperty("user_name")
    private String userName;

    @JsonProperty("vip_level")
    private String vipLevel;
}
