package com.authvault.service.impl;

import com.authvault.exception.VerificationException;
import org.junit.jupiter.api.Test;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerceptualHashServiceImplTest {

    private final PerceptualHashServiceImpl service = new PerceptualHashServiceImpl();

    @Test
    void sameImageProducesDeterministicFixedLowercaseHashAndZeroDistance() throws Exception {
        byte[] image = encode(createPattern(160, 120), "png");

        String first = hash(image);
        String second = hash(image);

        assertEquals(first, second);
        assertTrue(first.matches("[0-9a-f]{16}"));
        assertEquals(0, service.hammingDistance(first, second));
    }

    @Test
    void resizedAndRecompressedVariantsRemainCloserThanUnrelatedImage() throws Exception {
        BufferedImage original = createPattern(160, 120);
        BufferedImage resized = resize(original, 320, 240);
        BufferedImage unrelated = createUnrelatedPattern(160, 120);

        String originalHash = hash(encode(original, "png"));
        String resizedHash = hash(encode(resized, "png"));
        String recompressedHash = hash(encodeJpeg(original, 0.35f));
        String unrelatedHash = hash(encode(unrelated, "png"));

        int unrelatedDistance = service.hammingDistance(originalHash, unrelatedHash);
        assertTrue(service.hammingDistance(originalHash, resizedHash) < unrelatedDistance);
        assertTrue(service.hammingDistance(originalHash, recompressedHash) < unrelatedDistance);
    }

    @Test
    void mildBrightnessChangeRemainsCloserThanUnrelatedImage() throws Exception {
        BufferedImage original = createPattern(160, 120);
        BufferedImage brighter = adjustBrightness(original, 18);
        BufferedImage unrelated = createUnrelatedPattern(160, 120);

        String originalHash = hash(encode(original, "png"));
        int changedDistance = service.hammingDistance(
                originalHash, hash(encode(brighter, "png")));
        int unrelatedDistance = service.hammingDistance(
                originalHash, hash(encode(unrelated, "png")));

        assertTrue(changedDistance < unrelatedDistance);
    }

    @Test
    void hammingDistanceIsBoundedAndRejectsMalformedHashes() {
        assertEquals(64, service.hammingDistance("0000000000000000", "ffffffffffffffff"));
        assertThrows(IllegalArgumentException.class,
                () -> service.hammingDistance("abc", "0000000000000000"));
        assertThrows(IllegalArgumentException.class,
                () -> service.hammingDistance("ABCDEF0123456789", "0000000000000000"));
        assertThrows(IllegalArgumentException.class,
                () -> service.hammingDistance(null, "0000000000000000"));
    }

    @Test
    void rejectsUndecodableImageData() {
        assertThrows(VerificationException.class,
                () -> hash("not-an-image".getBytes()));
    }

    private String hash(byte[] bytes) {
        return service.calculate(new ByteArrayInputStream(bytes));
    }

    private BufferedImage createPattern(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(230, 230, 220));
        graphics.fillRect(0, 0, width, height);
        graphics.setColor(new Color(25, 80, 180));
        graphics.fillOval(width / 10, height / 8, width / 2, height / 2);
        graphics.setColor(new Color(210, 50, 45));
        graphics.fillRect(width / 2, height / 2, width / 3, height / 3);
        graphics.setColor(Color.BLACK);
        graphics.drawLine(0, height - 1, width - 1, 0);
        graphics.dispose();
        return image;
    }

    private BufferedImage createUnrelatedPattern(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                boolean stripe = ((x / 10) + (y / 8)) % 2 == 0;
                image.setRGB(x, y, stripe ? Color.YELLOW.getRGB() : Color.DARK_GRAY.getRGB());
            }
        }
        return image;
    }

    private BufferedImage resize(BufferedImage source, int width, int height) {
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = resized.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(source, 0, 0, width, height, null);
        graphics.dispose();
        return resized;
    }

    private BufferedImage adjustBrightness(BufferedImage source, int adjustment) {
        BufferedImage adjusted = new BufferedImage(
                source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                Color color = new Color(source.getRGB(x, y));
                adjusted.setRGB(x, y, new Color(
                        Math.min(255, color.getRed() + adjustment),
                        Math.min(255, color.getGreen() + adjustment),
                        Math.min(255, color.getBlue() + adjustment)).getRGB());
            }
        }
        return adjusted;
    }

    private byte[] encode(BufferedImage image, String format) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, format, output));
        return output.toByteArray();
    }

    private byte[] encodeJpeg(BufferedImage image, float quality) throws Exception {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        ImageWriter writer = writers.next();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(imageOutput);
            ImageWriteParam parameters = writer.getDefaultWriteParam();
            parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parameters.setCompressionQuality(quality);
            writer.write(null, new IIOImage(image, null, null), parameters);
        } finally {
            writer.dispose();
        }
        return output.toByteArray();
    }
}
