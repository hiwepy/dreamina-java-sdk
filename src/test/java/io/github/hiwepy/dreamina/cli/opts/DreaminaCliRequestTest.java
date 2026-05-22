package io.github.hiwepy.dreamina.cli.opts;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
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

    @Test
    void multiframe2VideoRequest_shouldRepeatTransitionPrompts() throws IOException {
        Path a = createTempFile("a.png");
        Path b = createTempFile("b.png");
        Path c = createTempFile("c.png");
        DreaminaMultiframe2VideoRequest request = DreaminaMultiframe2VideoRequest.builder()
            .image(a.toString())
            .image(b.toString())
            .image(c.toString())
            .transitionPrompt("A to B")
            .transitionPrompt("B to C")
            .transitionDuration("3")
            .transitionDuration("4")
            .pollSeconds(0)
            .build();

        List<String> args = request.toCliArgs();
        assertTrue(args.contains("--transition-prompt=A to B"));
        assertTrue(args.contains("--transition-prompt=B to C"));
        assertTrue(args.contains("--transition-duration=3"));
        assertTrue(args.contains("--transition-duration=4"));
    }

    @Test
    void multimodal2VideoRequest_shouldRequireAtLeastOneMedia() {
        DreaminaMultimodal2VideoRequest request = DreaminaMultimodal2VideoRequest.builder()
            .prompt("人物跟随音乐节奏摆动")
            .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, request::toCliArgs);
        assertTrue(ex.getMessage().contains("at least one image, video or audio"));
    }

    @Test
    void multimodal2VideoRequest_shouldRepeatImageFlags() throws IOException {
        Path a = createTempFile("a.png");
        Path b = createTempFile("b.png");
        DreaminaMultimodal2VideoRequest request = DreaminaMultimodal2VideoRequest.builder()
            .image(a.toString())
            .image(b.toString())
            .prompt("cinematic")
            .durationSeconds(5)
            .videoResolution(DreaminaVideoResolutionType.RESOLUTION_1080P)
            .pollSeconds(0)
            .build();

        List<String> args = request.toCliArgs();
        assertTrue(args.contains("--image=" + a));
        assertTrue(args.contains("--image=" + b));
        assertTrue(args.contains("--video_resolution=1080p"));
    }

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
        assertTrue(args.contains("--video_resolution=720p"));
    }

    @Test
    void imageUpscaleRequest_shouldSupport8k() throws IOException {
        Path image = createTempFile("input.png");
        DreaminaImageUpscaleRequest request = DreaminaImageUpscaleRequest.builder()
            .imagePath(image.toString())
            .resolutionType(DreaminaImageResolutionType.RESOLUTION_8K)
            .pollSeconds(0)
            .build();

        List<String> args = request.toCliArgs();
        assertTrue(args.contains("--resolution_type=8k"));
    }

    @Test
    void queryResultRequest_shouldBuildDownloadDir() {
        DreaminaQueryResultRequest request = DreaminaQueryResultRequest.builder()
            .submitId("abc-123")
            .downloadDir("./downloads")
            .build();

        List<String> args = request.toCliArgs();
        assertTrue(args.contains("--submit_id=abc-123"));
        assertTrue(args.contains("--download_dir=./downloads"));
    }

    @Test
    void listTaskRequest_shouldBuildFilters() {
        DreaminaListTaskRequest request = DreaminaListTaskRequest.builder()
            .genStatus("success")
            .genTaskType("text2image")
            .limit(10)
            .offset(0)
            .build();

        List<String> args = request.toCliArgs();
        assertTrue(args.contains("--gen_status=success"));
        assertTrue(args.contains("--gen_task_type=text2image"));
        assertTrue(args.contains("--limit=10"));
        assertTrue(args.contains("--offset=0"));
    }

    @Test
    void text2VideoRequest_shouldRejectNonSeedanceModel() {
        DreaminaText2VideoRequest request = DreaminaText2VideoRequest.builder()
            .prompt("test")
            .modelVersion(DreaminaVideoModelVersion.MODEL_3_0)
            .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, request::toCliArgs);
        assertTrue(ex.getMessage().contains("seedance"));
    }

    private Path createTempFile(String fileName) throws IOException {
        Path file = tempDir.resolve(fileName);
        Files.writeString(file, "demo");
        return file;
    }
}
