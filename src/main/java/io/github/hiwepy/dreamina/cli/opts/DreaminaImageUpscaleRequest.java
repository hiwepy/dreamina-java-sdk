package io.github.hiwepy.dreamina.cli.opts;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

/**
 * 图像超分请求对象。
 *
 * @author wandl
 * @since 1.0.0
 */
@Getter
@Builder
public class DreaminaImageUpscaleRequest implements DreaminaCliArgumentProvider {

    private final String imagePath;
    private final DreaminaImageResolutionType resolutionType;
    private final Long sessionId;
    private final Integer pollSeconds;

    @Singular("additionalArg")
    private final List<String> additionalRawArgs;

    @Override
    public List<String> toCliArgs() {
        List<String> args = new ArrayList<>();
        DreaminaCliRequestSupport.addFlag(
            args, "--image", DreaminaCliRequestSupport.requireReadableFile(imagePath, "imagePath"));
        if (resolutionType == DreaminaImageResolutionType.RESOLUTION_1K) {
            throw new IllegalArgumentException("image_upscale only supports 2k, 4k or 8k resolution");
        }
        DreaminaCliRequestSupport.addFlag(
            args, "--resolution_type", resolutionType == null ? null : resolutionType.getCliValue());
        DreaminaCliRequestSupport.requireSessionId(sessionId);
        DreaminaCliRequestSupport.addFlag(args, "--session", sessionId);
        DreaminaCliRequestSupport.requireNonNegative(pollSeconds, "pollSeconds");
        DreaminaCliRequestSupport.addFlag(args, "--poll", pollSeconds);
        DreaminaCliRequestSupport.addAdditionalArgs(args, additionalRawArgs);
        return args;
    }
}
