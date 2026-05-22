package io.github.hiwepy.dreamina.cli.opts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

/**
 * 多模态视频请求对象。
 *
 * @author wandl
 * @since 1.0.0
 */
@Getter
@Builder
public class DreaminaMultimodal2VideoRequest implements DreaminaCliArgumentProvider {

    @Singular("image")
    private final List<String> images;

    @Singular("video")
    private final List<String> videos;

    @Singular("audio")
    private final List<String> audios;

    private final String prompt;
    private final Integer durationSeconds;
    private final DreaminaRatio ratio;

    @Builder.Default
    private final DreaminaVideoModelVersion modelVersion = DreaminaVideoModelVersion.SEEDANCE_2_0_FAST;

    @Builder.Default
    private final DreaminaVideoResolutionType videoResolution = DreaminaVideoResolutionType.RESOLUTION_720P;

    private final Long sessionId;
    private final Integer pollSeconds;

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
        if (modelVersion != null && !modelVersion.supportsText2Video()) {
            throw new IllegalArgumentException("multimodal2video requires seedance model family");
        }
        List<String> args = new ArrayList<>();
        DreaminaCliRequestSupport.addRepeatedFlag(args, "--image", cleanedImages);
        DreaminaCliRequestSupport.addRepeatedFlag(args, "--video", cleanedVideos);
        DreaminaCliRequestSupport.addRepeatedFlag(args, "--audio", cleanedAudios);
        DreaminaCliRequestSupport.addFlag(args, "--prompt", prompt);
        DreaminaCliRequestSupport.requireRange(durationSeconds, 4, 15, "durationSeconds");
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
