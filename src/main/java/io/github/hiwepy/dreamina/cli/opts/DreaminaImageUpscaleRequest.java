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

    /**
     * 需要放大的本地图片。
     */
    private final String imagePath;

    /**
     * 目标分辨率。
     */
    private final DreaminaImageResolutionType resolutionType;

    /**
     * 额外原生参数。
     */
    @Singular("additionalArg")
    private final List<String> additionalRawArgs;

    @Override
    public List<String> toCliArgs() {
        List<String> args = new ArrayList<>();
        DreaminaCliRequestSupport.addFlag(
            args, "--image", DreaminaCliRequestSupport.requireReadableFile(imagePath, "imagePath"));
        DreaminaCliRequestSupport.addFlag(
            args, "--resolution_type", resolutionType == null ? null : resolutionType.getCliValue());
        DreaminaCliRequestSupport.addAdditionalArgs(args, additionalRawArgs);
        return args;
    }
}
