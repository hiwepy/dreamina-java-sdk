package io.github.hiwepy.dreamina.cli.opts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

/**
 * 多模态视频请求对象。
 * <p>
 * 按技能约束：至少需要 image/video/audio 任一输入，且最多 9 张图、3 段视频、3 段音频。
 * </p>
 *
 * @author wandl
 * @since 1.0.0
 */
@Getter
@Builder
public class DreaminaMultimodal2VideoRequest implements DreaminaCliArgumentProvider {

    /**
     * 参考图片，最多 9 张。
     */
    @Singular("image")
    private final List<String> images;

    /**
     * 参考视频，最多 3 段。
     */
    @Singular("video")
    private final List<String> videos;

    /**
     * 参考音频，最多 3 段。
     */
    @Singular("audio")
    private final List<String> audios;

    /**
     * 合成提示词。
     */
    private final String prompt;

    /**
     * 时长（秒）。
     */
    private final Integer durationSeconds;

    /**
     * 宽高比。
     */
    private final DreaminaRatio ratio;

    /**
     * 多模态视频模型版本。
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
        List<String> cleanedImages = images == null || images.isEmpty() ? Collections.emptyList()
            : DreaminaCliRequestSupport.requireReadableFiles(images, "images", 1, 9);
        List<String> cleanedVideos = videos == null || videos.isEmpty() ? Collections.emptyList()
            : DreaminaCliRequestSupport.requireReadableFiles(videos, "videos", 1, 3);
        List<String> cleanedAudios = audios == null || audios.isEmpty() ? Collections.emptyList()
            : DreaminaCliRequestSupport.requireReadableFiles(audios, "audios", 1, 3);
        if (cleanedImages.isEmpty() && cleanedVideos.isEmpty() && cleanedAudios.isEmpty()) {
            throw new IllegalArgumentException("multimodal2video requires at least one image, video or audio input");
        }
        List<String> args = new ArrayList<>();
        DreaminaCliRequestSupport.addFlag(
            args, "--image", cleanedImages.isEmpty() ? null : DreaminaCliRequestSupport.csv(cleanedImages));
        DreaminaCliRequestSupport.addFlag(
            args, "--video", cleanedVideos.isEmpty() ? null : DreaminaCliRequestSupport.csv(cleanedVideos));
        DreaminaCliRequestSupport.addFlag(
            args, "--audio", cleanedAudios.isEmpty() ? null : DreaminaCliRequestSupport.csv(cleanedAudios));
        DreaminaCliRequestSupport.addFlag(args, "--prompt", prompt);
        DreaminaCliRequestSupport.requireRange(durationSeconds, 4, 15, "durationSeconds");
        DreaminaCliRequestSupport.addFlag(args, "--duration", durationSeconds);
        DreaminaCliRequestSupport.addFlag(args, "--ratio", ratio == null ? null : ratio.getCliValue());
        DreaminaCliRequestSupport.addFlag(args, "--model_version", modelVersion == null ? null : modelVersion.getCliValue());
        DreaminaCliRequestSupport.requireNonNegative(pollSeconds, "pollSeconds");
        DreaminaCliRequestSupport.addFlag(args, "--poll", pollSeconds);
        DreaminaCliRequestSupport.addAdditionalArgs(args, additionalRawArgs);
        return args;
    }
}
