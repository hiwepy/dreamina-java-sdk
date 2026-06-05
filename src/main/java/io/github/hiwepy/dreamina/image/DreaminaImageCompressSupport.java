package io.github.hiwepy.dreamina.image;

import net.coobird.thumbnailator.Thumbnails;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Objects;

/**
 * 基于 Thumbnailator 的 Dreamina 生图字节压缩工具。
 *
 * <p>参考 playwright-spring-boot-starter {@code WkhtmlToImageBufferRenderStrategy#compressScreenshot}：
 * 从字节流读入，{@code scale} + {@code outputQuality} 后写回字节数组。</p>
 *
 * @author wandl
 * @since 1.0.0
 */
public final class DreaminaImageCompressSupport {

    private static final int MIN_QUALITY = 1;
    private static final int MAX_QUALITY = 100;
    private static final double MIN_SCALE = 0.01d;
    private static final double MAX_SCALE = 8.0d;

    private DreaminaImageCompressSupport() {
    }

    /**
     * 按配置压缩图片字节；未启用或输入为空时原样返回。
     *
     * @param source     原始图片字节
     * @param formatHint 格式提示（URL 后缀或文件名，如 {@code .jpg}、{@code png}）
     * @param options    压缩选项
     * @return 压缩后字节；未压缩时返回 {@code source} 本身
     * @throws IOException 图片不可读或编码失败
     */
    public static byte[] compressIfEnabled(byte[] source, String formatHint, DreaminaImageCompressOptions options)
            throws IOException {
        DreaminaImageCompressResult result = compress(source, formatHint, options);
        return result.getBytes();
    }

    /**
     * 按配置压缩图片字节并返回摘要。
     *
     * @param source     原始图片字节
     * @param formatHint 格式提示
     * @param options    压缩选项
     * @return 压缩结果
     * @throws IOException 图片不可读或编码失败
     */
    public static DreaminaImageCompressResult compress(byte[] source, String formatHint,
            DreaminaImageCompressOptions options) throws IOException {
        Objects.requireNonNull(source, "source");
        if (options == null || !options.isEnabled() || source.length == 0) {
            return DreaminaImageCompressResult.builder()
                    .bytes(source)
                    .applied(false)
                    .originalSize(source.length)
                    .compressedSize(source.length)
                    .scale(1.0d)
                    .quality(clampQuality(options != null ? options.getQuality() : MAX_QUALITY))
                    .outputFormat(resolveOutputFormat(formatHint, null))
                    .build();
        }

        BufferedImage image = decodeImage(source);
        return compressDecoded(image, source.length, formatHint, options);
    }

    /**
     * 将 {@link BufferedImage} 压缩为字节（供测试或本地读盘场景）。
     *
     * @param image      原图
     * @param formatHint 格式提示
     * @param options    压缩选项
     * @return 压缩结果
     * @throws IOException 编码失败
     */
    public static DreaminaImageCompressResult compress(BufferedImage image, String formatHint,
            DreaminaImageCompressOptions options) throws IOException {
        Objects.requireNonNull(image, "image");
        if (options == null || !options.isEnabled()) {
            String outputFormat = resolveOutputFormat(formatHint, null);
            byte[] bytes = encodeImage(image, outputFormat);
            return DreaminaImageCompressResult.builder()
                    .bytes(bytes)
                    .applied(false)
                    .originalSize(bytes.length)
                    .compressedSize(bytes.length)
                    .scale(1.0d)
                    .quality(clampQuality(options != null ? options.getQuality() : MAX_QUALITY))
                    .outputFormat(outputFormat)
                    .build();
        }
        String outputFormat = resolveOutputFormat(formatHint, null);
        byte[] baseline = encodeImage(image, outputFormat);
        return compressDecoded(image, baseline.length, formatHint, options);
    }

    /**
     * 单次解码字节为 {@link BufferedImage}。
     *
     * @param source 原始图片字节
     * @return 解码后的图片
     * @throws IOException 不可读
     */
    private static BufferedImage decodeImage(byte[] source) throws IOException {
        try (ByteArrayInputStream input = new ByteArrayInputStream(source)) {
            BufferedImage image = ImageIO.read(input);
            if (image == null) {
                throw new IOException("Source bytes are not a readable image");
            }
            return image;
        }
    }

    /**
     * 对已解码图片执行 Thumbnailator 压缩并编码为字节（单次内存处理，不重复读流）。
     *
     * @param image           已解码原图
     * @param originalByteSize 原始字节长度（用于结果摘要）
     * @param formatHint      格式提示
     * @param options         压缩选项（已确认 enabled）
     * @return 压缩结果
     * @throws IOException 压缩或编码失败
     */
    private static DreaminaImageCompressResult compressDecoded(BufferedImage image, int originalByteSize,
            String formatHint, DreaminaImageCompressOptions options) throws IOException {
        double scale = normalizeScale(options.getScale());
        int quality = clampQuality(options.getQuality());
        String outputFormat = resolveOutputFormat(formatHint, null);

        byte[] compressed = encodeCompressed(image, outputFormat, scale, quality);
        if (compressed.length == 0) {
            throw new IOException("Thumbnailator produced empty output");
        }

        boolean applied = compressed.length != originalByteSize
                || Math.abs(scale - 1.0d) >= 1e-6
                || quality < MAX_QUALITY;
        return DreaminaImageCompressResult.builder()
                .bytes(compressed)
                .applied(applied)
                .originalSize(originalByteSize)
                .compressedSize(compressed.length)
                .scale(scale)
                .quality(quality)
                .outputFormat(outputFormat)
                .build();
    }

    /**
     * 使用 Thumbnailator 缩放/压质后写出为字节数组。
     *
     * @param image        原图
     * @param outputFormat ImageIO 格式名
     * @param scale        缩放比例
     * @param quality      输出质量 1–100
     * @return 压缩后字节
     * @throws IOException 处理失败
     */
    private static byte[] encodeCompressed(BufferedImage image, String outputFormat, double scale, int quality)
            throws IOException {
        Thumbnails.Builder<BufferedImage> builder = Thumbnails.of(image)
                .allowOverwrite(true)
                .outputFormat(outputFormat)
                .outputQuality(quality / 100f);
        if (Math.abs(scale - 1.0d) < 1e-6) {
            builder.scale(1f);
        } else {
            builder.scale((float) scale);
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            builder.toOutputStream(output);
            return output.toByteArray();
        }
    }

    /**
     * 将 {@link BufferedImage} 编码为字节（不缩放）。
     *
     * @param image        图片
     * @param outputFormat ImageIO 格式名
     * @return 编码字节
     * @throws IOException 编码失败
     */
    private static byte[] encodeImage(BufferedImage image, String outputFormat) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, outputFormat, output)) {
                throw new IOException("Cannot encode image as " + outputFormat);
            }
            return output.toByteArray();
        }
    }

    /**
     * 规范化缩放比例到合法区间。
     *
     * @param scale 原始比例
     * @return 合法比例
     */
    static double normalizeScale(double scale) {
        if (Double.isNaN(scale) || Double.isInfinite(scale)) {
            return 1.0d;
        }
        if (scale < MIN_SCALE) {
            return MIN_SCALE;
        }
        if (scale > MAX_SCALE) {
            return MAX_SCALE;
        }
        return scale;
    }

    /**
     * 将质量限制在 1–100。
     *
     * @param quality 原始质量
     * @return 合法质量
     */
    static int clampQuality(int quality) {
        if (quality < MIN_QUALITY) {
            return MIN_QUALITY;
        }
        if (quality > MAX_QUALITY) {
            return MAX_QUALITY;
        }
        return quality;
    }

    /**
     * 从 URL/文件名后缀推断 ImageIO 输出格式名。
     *
     * @param formatHint   后缀或格式名
     * @param defaultFormat 默认格式
     * @return jpg / png / gif / bmp
     */
    static String resolveOutputFormat(String formatHint, String defaultFormat) {
        String fallback = defaultFormat != null ? defaultFormat : "jpg";
        if (formatHint == null || formatHint.isBlank()) {
            return fallback;
        }
        String normalized = formatHint.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        return switch (normalized) {
            case "png" -> "png";
            case "gif" -> "gif";
            case "bmp" -> "bmp";
            case "jpeg", "jpg" -> "jpg";
            case "webp" -> "jpg";
            default -> fallback;
        };
    }
}
