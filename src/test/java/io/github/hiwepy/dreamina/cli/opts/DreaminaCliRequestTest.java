package io.github.hiwepy.dreamina.cli.opts;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dreamina 强类型请求对象测试。
 *
 * @author wandl
 * @since 1.0.0
 */
class DreaminaCliRequestTest {

    @TempDir
    Path tempDir;

    /**
     * 文生图请求应输出稳定 argv。
     *
     * @throws IOException 文件准备失败
     */
    @Test
    void text2ImageRequest_shouldBuildExpectedArgs() throws IOException {
        DreaminaText2ImageRequest request = DreaminaText2ImageRequest.builder()
            .prompt("一只布偶猫蹲在窗边")
            .ratio(DreaminaRatio.RATIO_1_1)
            .modelVersion(DreaminaImageModelVersion.MODEL_5_0)
            .resolutionType(DreaminaImageResolutionType.RESOLUTION_4K)
            .sessionId(42L)
            .pollSeconds(30)
            .additionalArg("--debug")
            .build();

        List<String> args = request.toCliArgs();
        assertEquals("--prompt=一只布偶猫蹲在窗边", args.get(0));
        assertTrue(args.contains("--ratio=1:1"));
        assertTrue(args.contains("--model_version=5.0"));
        assertTrue(args.contains("--resolution_type=4k"));
        assertTrue(args.contains("--session=42"));
        assertTrue(args.contains("--poll=30"));
        assertTrue(args.contains("--debug"));
    }

    /**
     * 图生图必须校验图片存在且限制为 2k/4k + 4.0+。
     *
     * @throws IOException 文件准备失败
     */
    @Test
    void image2ImageRequest_shouldRejectUnsupportedResolution() throws IOException {
        Path image = createTempFile("input.png");
        DreaminaImage2ImageRequest request = DreaminaImage2ImageRequest.builder()
            .image(image.toString())
            .prompt("保持主体不变，改成水彩风格")
            .modelVersion(DreaminaImageModelVersion.MODEL_4_5)
            .resolutionType(DreaminaImageResolutionType.RESOLUTION_1K)
            .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, request::toCliArgs);
        assertTrue(ex.getMessage().contains("2k or 4k"));
    }

    /**
     * 多帧视频必须限制在 2-20 张。
     *
     * @throws IOException 文件准备失败
     */
    @Test
    void multiframe2VideoRequest_shouldEnforceImageCount() throws IOException {
        Path image = createTempFile("story-1.png");
        DreaminaMultiframe2VideoRequest request = DreaminaMultiframe2VideoRequest.builder()
            .image(image.toString())
            .prompt("故事推进")
            .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, request::toCliArgs);
        assertTrue(ex.getMessage().contains("range [2, 20]"));
    }

    /**
     * 多模态请求至少要有一种媒体输入。
     */
    @Test
    void multimodal2VideoRequest_shouldRequireAtLeastOneMedia() {
        DreaminaMultimodal2VideoRequest request = DreaminaMultimodal2VideoRequest.builder()
            .prompt("人物跟随音乐节奏摆动")
            .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, request::toCliArgs);
        assertTrue(ex.getMessage().contains("at least one image, video or audio"));
    }

    /**
     * 首尾帧视频请求应输出命令参数。
     *
     * @throws IOException 文件准备失败
     */
    @Test
    void frames2VideoRequest_shouldBuildExpectedArgs() throws IOException {
        Path first = createTempFile("first.png");
        Path last = createTempFile("last.png");
        DreaminaFrames2VideoRequest request = DreaminaFrames2VideoRequest.builder()
            .firstImagePath(first.toString())
            .lastImagePath(last.toString())
            .prompt("花苞缓慢绽放")
            .durationSeconds(8)
            .modelVersion(DreaminaVideoModelVersion.SEEDANCE_2_0_FAST_VIP)
            .videoResolution(DreaminaVideoResolutionType.RESOLUTION_720P)
            .pollSeconds(0)
            .build();

        List<String> args = request.toCliArgs();
        assertTrue(args.contains("--first=" + first));
        assertTrue(args.contains("--last=" + last));
        assertTrue(args.contains("--prompt=花苞缓慢绽放"));
        assertTrue(args.contains("--duration=8"));
        assertTrue(args.contains("--model_version=seedance2.0fast_vip"));
        assertTrue(args.contains("--video_resolution=720P"));
        assertTrue(args.contains("--poll=0"));
    }

    /**
     * 创建临时媒体文件。
     *
     * @param fileName 文件名
     * @return 文件路径
     * @throws IOException 文件写入失败
     */
    private Path createTempFile(String fileName) throws IOException {
        Path file = tempDir.resolve(fileName);
        Files.writeString(file, "demo");
        return file;
    }
}
