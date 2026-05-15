package io.github.hiwepy.dreamina.cli.opts;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

/**
 * 图生图请求对象。
 * <p>
 * 按 Jimeng 技能约束：必须提供 1-10 张本地图片，且模型版本需为 4.0+，分辨率仅支持 2k/4k。
 * </p>
 *
 * @author wandl
 * @since 1.0.0
 */
@Getter
@Builder
public class DreaminaImage2ImageRequest implements DreaminaCliArgumentProvider {

    /**
     * 参考图片列表。
     */
    @Singular("image")
    private final List<String> images;

    /**
     * 编辑提示词。
     */
    private final String prompt;

    /**
     * 可选宽高比。
     */
    private final DreaminaRatio ratio;

    /**
     * 图生图默认使用 5.0。
     */
    @Builder.Default
    private final DreaminaImageModelVersion modelVersion = DreaminaImageModelVersion.MODEL_5_0;

    /**
     * 图生图默认 2k。
     */
    @Builder.Default
    private final DreaminaImageResolutionType resolutionType = DreaminaImageResolutionType.RESOLUTION_2K;

    /**
     * 会话 ID。
     */
    private final Long sessionId;

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
        List<String> cleanedImages = DreaminaCliRequestSupport.requireReadableFiles(images, "images", 1, 10);
        if (modelVersion == null || !modelVersion.supportsImageToImage()) {
            throw new IllegalArgumentException("image2image requires modelVersion 4.0+");
        }
        if (resolutionType == DreaminaImageResolutionType.RESOLUTION_1K) {
            throw new IllegalArgumentException("image2image only supports 2k or 4k resolution");
        }
        List<String> args = new ArrayList<>();
        DreaminaCliRequestSupport.addFlag(args, "--images", DreaminaCliRequestSupport.csv(cleanedImages));
        DreaminaCliRequestSupport.addFlag(args, "--prompt", DreaminaCliRequestSupport.requireNonBlank(prompt, "prompt"));
        DreaminaCliRequestSupport.addFlag(args, "--ratio", ratio == null ? null : ratio.getCliValue());
        DreaminaCliRequestSupport.addFlag(args, "--model_version", modelVersion.getCliValue());
        DreaminaCliRequestSupport.addFlag(args, "--resolution_type", resolutionType.getCliValue());
        DreaminaCliRequestSupport.requireSessionId(sessionId);
        DreaminaCliRequestSupport.addFlag(args, "--session", sessionId);
        DreaminaCliRequestSupport.requireNonNegative(pollSeconds, "pollSeconds");
        DreaminaCliRequestSupport.addFlag(args, "--poll", pollSeconds);
        DreaminaCliRequestSupport.addAdditionalArgs(args, additionalRawArgs);
        return args;
    }
}
