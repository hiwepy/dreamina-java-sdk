package io.github.hiwepy.dreamina.cli.opts;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

/**
 * 文生图请求对象。
 *
 * @author wandl
 * @since 1.0.0
 */
@Getter
@Builder
public class DreaminaText2ImageRequest implements DreaminaCliArgumentProvider {

    /**
     * 必填提示词。
     */
    private final String prompt;

    /**
     * 可选宽高比。
     */
    private final DreaminaRatio ratio;

    /**
     * 图像模型版本。
     * <p>
     * 文生图默认使用 4.0，便于与当前业务侧默认策略保持一致；调用方仍可显式覆盖为 4.1/4.5/4.6/5.0。
     * </p>
     */
    @Builder.Default
    private final DreaminaImageModelVersion modelVersion = DreaminaImageModelVersion.MODEL_4_0;

    /**
     * 可选分辨率。
     */
    private final DreaminaImageResolutionType resolutionType;

    /**
     * 会话 ID。
     */
    private final Long sessionId;

    /**
     * poll 秒数；0 表示异步。
     */
    private final Integer pollSeconds;

    /**
     * 额外原生参数。
     */
    @Singular("additionalArg")
    private final List<String> additionalRawArgs;

    @Override
    public List<String> toCliArgs() {
        List<String> args = new ArrayList<>();
        DreaminaCliRequestSupport.addFlag(args, "--prompt", DreaminaCliRequestSupport.requireNonBlank(prompt, "prompt"));
        DreaminaCliRequestSupport.addFlag(args, "--ratio", ratio == null ? null : ratio.getCliValue());
        DreaminaCliRequestSupport.addFlag(args, "--model_version", modelVersion == null ? null : modelVersion.getCliValue());
        DreaminaCliRequestSupport.addFlag(args, "--resolution_type", resolutionType == null ? null : resolutionType.getCliValue());
        DreaminaCliRequestSupport.requireSessionId(sessionId);
        DreaminaCliRequestSupport.addFlag(args, "--session", sessionId);
        DreaminaCliRequestSupport.requireNonNegative(pollSeconds, "pollSeconds");
        DreaminaCliRequestSupport.addFlag(args, "--poll", pollSeconds);
        DreaminaCliRequestSupport.addAdditionalArgs(args, additionalRawArgs);
        return args;
    }
}
