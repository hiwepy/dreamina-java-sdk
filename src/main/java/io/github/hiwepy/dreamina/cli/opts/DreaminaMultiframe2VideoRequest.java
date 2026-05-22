package io.github.hiwepy.dreamina.cli.opts;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

/**
 * 多帧故事视频请求对象。
 * <p>
 * 对齐 {@code dreamina multiframe2video -h}：2 张图用 {@code --prompt}/{@code --duration}；
 * 3 张及以上重复 {@code --transition-prompt}/{@code --transition-duration}；不支持 model/resolution 覆写。
 * </p>
 *
 * @author wandl
 * @since 1.0.0
 */
@Getter
@Builder
public class DreaminaMultiframe2VideoRequest implements DreaminaCliArgumentProvider {

    @Singular("image")
    private final List<String> images;

    /** 2 张图时的叙事提示词。 */
    private final String prompt;

    /** 2 张图时的过渡时长（秒，[0.5,8]）。 */
    private final Double durationSeconds;

    /** 3+ 张图时每段 transition 提示词（N 张图需 N-1 条）。 */
    @Singular("transitionPrompt")
    private final List<String> transitionPrompts;

    /** 3+ 张图时每段 transition 时长。 */
    @Singular("transitionDuration")
    private final List<String> transitionDurations;

    private final Long sessionId;
    private final Integer pollSeconds;

    @Singular("additionalArg")
    private final List<String> additionalRawArgs;

    @Override
    public List<String> toCliArgs() {
        List<String> cleanedImages = DreaminaCliRequestSupport.requireReadableFiles(images, "images", 2, 20);
        List<String> args = new ArrayList<>();
        DreaminaCliRequestSupport.addFlag(args, "--images", DreaminaCliRequestSupport.csv(cleanedImages));

        if (cleanedImages.size() == 2) {
            DreaminaCliRequestSupport.addFlag(args, "--prompt", prompt);
            DreaminaCliRequestSupport.requireDoubleRange(durationSeconds, 0.5, 8.0, "durationSeconds");
            DreaminaCliRequestSupport.addFlag(args, "--duration", durationSeconds);
        } else {
            if (transitionPrompts != null && !transitionPrompts.isEmpty()) {
                int expected = cleanedImages.size() - 1;
                if (transitionPrompts.size() != expected) {
                    throw new IllegalArgumentException(
                        "transitionPrompts size must be " + expected + " for " + cleanedImages.size() + " images");
                }
                DreaminaCliRequestSupport.addRepeatedFlag(args, "--transition-prompt", transitionPrompts);
            }
            if (transitionDurations != null && !transitionDurations.isEmpty()) {
                int expected = cleanedImages.size() - 1;
                if (transitionDurations.size() != expected) {
                    throw new IllegalArgumentException(
                        "transitionDurations size must be " + expected + " for " + cleanedImages.size() + " images");
                }
                DreaminaCliRequestSupport.addRepeatedFlag(args, "--transition-duration", transitionDurations);
            }
        }

        DreaminaCliRequestSupport.requireSessionId(sessionId);
        DreaminaCliRequestSupport.addFlag(args, "--session", sessionId);
        DreaminaCliRequestSupport.requireNonNegative(pollSeconds, "pollSeconds");
        DreaminaCliRequestSupport.addFlag(args, "--poll", pollSeconds);
        DreaminaCliRequestSupport.addAdditionalArgs(args, additionalRawArgs);
        return args;
    }
}
