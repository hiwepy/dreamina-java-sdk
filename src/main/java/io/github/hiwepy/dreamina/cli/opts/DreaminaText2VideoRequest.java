package io.github.hiwepy.dreamina.cli.opts;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

/**
 * 文生视频请求对象。
 *
 * @author wandl
 * @since 1.0.0
 */
@Getter
@Builder
public class DreaminaText2VideoRequest implements DreaminaCliArgumentProvider {

    /**
     * 视频提示词，应包含动作与运镜描述。
     */
    private final String prompt;

    /**
     * 视频时长（秒）。
     */
    private final Integer durationSeconds;

    /**
     * 宽高比。
     */
    private final DreaminaRatio ratio;

    /**
     * 视频模型版本，默认快速模式。
     */
    @Builder.Default
    private final DreaminaVideoModelVersion modelVersion = DreaminaVideoModelVersion.SEEDANCE_2_0_FAST;

    /**
     * 视频分辨率。
     */
    @Builder.Default
    private final DreaminaVideoResolutionType videoResolution = DreaminaVideoResolutionType.RESOLUTION_720P;

    /**
     * 会话 ID。
     */
    private final Long sessionId;

    /**
     * poll 秒数，0 表示异步。
     */
    private final Integer pollSeconds;

    /**
     * 自定义参数。
     */
    @Singular("additionalArg")
    private final List<String> additionalRawArgs;

    @Override
    public List<String> toCliArgs() {
        if (modelVersion != null && !modelVersion.supportsText2Video()) {
            throw new IllegalArgumentException("text2video only supports seedance model family");
        }
        List<String> args = new ArrayList<>();
        DreaminaCliRequestSupport.addFlag(args, "--prompt", DreaminaCliRequestSupport.requireNonBlank(prompt, "prompt"));
        DreaminaCliRequestSupport.requireVideoDuration(durationSeconds, modelVersion, "durationSeconds");
        DreaminaCliRequestSupport.addFlag(args, "--duration", durationSeconds);
        DreaminaCliRequestSupport.addFlag(args, "--ratio", ratio == null ? null : ratio.getCliValue());
        DreaminaCliRequestSupport.addFlag(args, "--model_version", modelVersion == null ? null : modelVersion.getCliValue());
        DreaminaCliRequestSupport.addFlag(args, "--video_resolution", videoResolution == null ? null : videoResolution.getCliValue());
        DreaminaCliRequestSupport.requireSessionId(sessionId);
        DreaminaCliRequestSupport.addFlag(args, "--session", sessionId);
        DreaminaCliRequestSupport.requireNonNegative(pollSeconds, "pollSeconds");
        DreaminaCliRequestSupport.addFlag(args, "--poll", pollSeconds);
        DreaminaCliRequestSupport.addAdditionalArgs(args, additionalRawArgs);
        return args;
    }
}
