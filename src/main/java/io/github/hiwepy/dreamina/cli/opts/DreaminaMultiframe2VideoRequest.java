package io.github.hiwepy.dreamina.cli.opts;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

/**
 * 多帧故事视频请求对象。
 *
 * @author wandl
 * @since 1.0.0
 */
@Getter
@Builder
public class DreaminaMultiframe2VideoRequest implements DreaminaCliArgumentProvider {

    /**
     * 故事板图片，2-20 张。
     */
    @Singular("image")
    private final List<String> images;

    /**
     * 叙事提示词。
     */
    private final String prompt;

    /**
     * 帧间过渡描述。
     */
    private final String transitionPrompt;

    /**
     * 帧间过渡时长。
     */
    private final Integer transitionDurationSeconds;

    /**
     * 总时长。
     */
    private final Integer durationSeconds;

    /**
     * 模型版本；multiframe2video 不提供分辨率覆写。
     */
    @Builder.Default
    private final DreaminaVideoModelVersion modelVersion = DreaminaVideoModelVersion.SEEDANCE_2_0_FAST;

    /**
     * poll 秒数。
     */
    private final Integer pollSeconds;

    /**
     * 额外原生参数。
     */
    @Singular("additionalArg")
    private final List<String> additionalRawArgs;

    @Override
    public List<String> toCliArgs() {
        List<String> cleanedImages = DreaminaCliRequestSupport.requireReadableFiles(images, "images", 2, 20);
        List<String> args = new ArrayList<>();
        DreaminaCliRequestSupport.addFlag(args, "--images", DreaminaCliRequestSupport.csv(cleanedImages));
        DreaminaCliRequestSupport.addFlag(args, "--prompt", prompt);
        DreaminaCliRequestSupport.addFlag(args, "--transition-prompt", transitionPrompt);
        DreaminaCliRequestSupport.requireNonNegative(transitionDurationSeconds, "transitionDurationSeconds");
        DreaminaCliRequestSupport.addFlag(args, "--transition-duration", transitionDurationSeconds);
        DreaminaCliRequestSupport.requireRange(durationSeconds, 4, 15, "durationSeconds");
        DreaminaCliRequestSupport.addFlag(args, "--duration", durationSeconds);
        DreaminaCliRequestSupport.addFlag(args, "--model_version", modelVersion == null ? null : modelVersion.getCliValue());
        DreaminaCliRequestSupport.requireNonNegative(pollSeconds, "pollSeconds");
        DreaminaCliRequestSupport.addFlag(args, "--poll", pollSeconds);
        DreaminaCliRequestSupport.addAdditionalArgs(args, additionalRawArgs);
        return args;
    }
}
