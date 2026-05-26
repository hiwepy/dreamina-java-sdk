package io.github.hiwepy.dreamina.cli.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;
import lombok.Data;

/**
 * {@code list_task} 等命令返回的 {@code commerce_info} 计费/权益摘要。
 *
 * @author wandl
 * @since 1.0.0
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DreaminaCommerceInfo {

    @JsonProperty("credit_count")
    private Long creditCount;

    /**
     * 单条三元组占位（生产环境可能各字段为空字符串）。
     */
    private DreaminaCommerceTriplet triplet;

    /**
     * 实际生效的权益三元组列表。
     */
    private List<DreaminaCommerceTriplet> triplets;

    /**
     * @return 非 null 的 triplets 视图
     */
    public List<DreaminaCommerceTriplet> safeTriplets() {
        return triplets == null ? Collections.emptyList() : triplets;
    }
}
