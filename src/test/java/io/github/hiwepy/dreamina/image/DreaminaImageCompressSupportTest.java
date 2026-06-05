package io.github.hiwepy.dreamina.image;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DreaminaImageCompressSupport} 单元测试。
 */
class DreaminaImageCompressSupportTest {

    @Test
    void compressIfDisabled_returnsOriginalBytes() throws Exception {
        byte[] source = encodePng(800, 600);
        DreaminaImageCompressOptions options = DreaminaImageCompressOptions.of(false, 0.5d, 50);

        byte[] result = DreaminaImageCompressSupport.compressIfEnabled(source, ".png", options);

        assertEquals(source, result);
    }

    @Test
    void compressWithScaleAndQuality_reducesByteSize() throws Exception {
        byte[] source = encodePng(2048, 2048);
        DreaminaImageCompressOptions options = DreaminaImageCompressOptions.of(true, 0.5d, 75);

        DreaminaImageCompressResult result = DreaminaImageCompressSupport.compress(source, ".png", options);

        assertTrue(result.isApplied());
        assertTrue(result.getCompressedSize() < result.getOriginalSize(), "compressed should be smaller");
        assertEquals(0.5d, result.getScale(), 1e-6);
        assertEquals(75, result.getQuality());
        assertEquals("png", result.getOutputFormat());
    }

    @Test
    void normalizeScale_clampsOutOfRange() {
        assertEquals(1.0d, DreaminaImageCompressSupport.normalizeScale(Double.NaN));
        assertEquals(0.01d, DreaminaImageCompressSupport.normalizeScale(0.001d));
        assertEquals(8.0d, DreaminaImageCompressSupport.normalizeScale(99d));
    }

    private static byte[] encodePng(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(120, 80, 200));
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
