package com.authvault.service.impl;

import com.authvault.exception.VerificationException;
import com.authvault.service.PerceptualHashService;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class PerceptualHashServiceImpl implements PerceptualHashService {

    // A 32x32 luminance sample and the lowest 8x8 DCT block produce exactly 64 bits.
    private static final int SAMPLE_SIZE = 32;
    private static final int LOW_FREQUENCY_SIZE = 8;
    private static final int HASH_BITS = LOW_FREQUENCY_SIZE * LOW_FREQUENCY_SIZE;
    private static final int HASH_HEX_LENGTH = HASH_BITS / 4;
    private static final Pattern HASH_PATTERN = Pattern.compile("^[0-9a-f]{16}$");
    private static final double[] DCT_SCALE = createDctScale();
    private static final double[][] COSINE = createCosineTable();

    @Override
    public String calculate(InputStream imageStream) {
        if (imageStream == null) {
            throw new VerificationException("Image data is required for perceptual hashing");
        }

        try {
            BufferedImage image = ImageIO.read(imageStream);
            if (image == null) {
                throw new VerificationException("Could not decode image for perceptual hashing");
            }

            double[][] luminance = resizeLuminance(image);
            double[] coefficients = lowFrequencyDct(luminance);
            double median = medianWithoutDc(coefficients);

            long hash = 0L;
            for (double coefficient : coefficients) {
                hash <<= 1;
                if (coefficient > median) {
                    hash |= 1L;
                }
            }
            return String.format(Locale.ROOT, "%0" + HASH_HEX_LENGTH + "x", hash);
        } catch (IOException exception) {
            throw new VerificationException("Could not read image for perceptual hashing", exception);
        }
    }

    @Override
    public int hammingDistance(String hashA, String hashB) {
        validateHash(hashA);
        validateHash(hashB);
        long left = Long.parseUnsignedLong(hashA, 16);
        long right = Long.parseUnsignedLong(hashB, 16);
        return Long.bitCount(left ^ right);
    }

    @Override
    public boolean isValidHash(String hash) {
        return hash != null && HASH_PATTERN.matcher(hash).matches();
    }

    private void validateHash(String hash) {
        if (!isValidHash(hash)) {
            throw new IllegalArgumentException("Perceptual hash must be 16 lowercase hexadecimal characters");
        }
    }

    private double[][] resizeLuminance(BufferedImage image) {
        int sourceWidth = image.getWidth();
        int sourceHeight = image.getHeight();
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            throw new VerificationException("Image dimensions are invalid for perceptual hashing");
        }

        double[][] resized = new double[SAMPLE_SIZE][SAMPLE_SIZE];
        for (int targetY = 0; targetY < SAMPLE_SIZE; targetY++) {
            double sourceY = ((targetY + 0.5) * sourceHeight / SAMPLE_SIZE) - 0.5;
            int y0 = clamp((int) Math.floor(sourceY), 0, sourceHeight - 1);
            int y1 = clamp(y0 + 1, 0, sourceHeight - 1);
            double yWeight = Math.max(0.0, sourceY - Math.floor(sourceY));

            for (int targetX = 0; targetX < SAMPLE_SIZE; targetX++) {
                double sourceX = ((targetX + 0.5) * sourceWidth / SAMPLE_SIZE) - 0.5;
                int x0 = clamp((int) Math.floor(sourceX), 0, sourceWidth - 1);
                int x1 = clamp(x0 + 1, 0, sourceWidth - 1);
                double xWeight = Math.max(0.0, sourceX - Math.floor(sourceX));

                double top = interpolate(luminance(image.getRGB(x0, y0)),
                        luminance(image.getRGB(x1, y0)), xWeight);
                double bottom = interpolate(luminance(image.getRGB(x0, y1)),
                        luminance(image.getRGB(x1, y1)), xWeight);
                resized[targetY][targetX] = interpolate(top, bottom, yWeight);
            }
        }
        return resized;
    }

    private double[] lowFrequencyDct(double[][] pixels) {
        double[] coefficients = new double[HASH_BITS];
        int index = 0;
        for (int verticalFrequency = 0; verticalFrequency < LOW_FREQUENCY_SIZE; verticalFrequency++) {
            for (int horizontalFrequency = 0; horizontalFrequency < LOW_FREQUENCY_SIZE; horizontalFrequency++) {
                double sum = 0.0;
                for (int y = 0; y < SAMPLE_SIZE; y++) {
                    for (int x = 0; x < SAMPLE_SIZE; x++) {
                        sum += pixels[y][x]
                                * COSINE[x][horizontalFrequency]
                                * COSINE[y][verticalFrequency];
                    }
                }
                coefficients[index++] = DCT_SCALE[horizontalFrequency]
                        * DCT_SCALE[verticalFrequency]
                        * sum;
            }
        }
        return coefficients;
    }

    private double medianWithoutDc(double[] coefficients) {
        double[] nonDc = Arrays.copyOfRange(coefficients, 1, coefficients.length);
        Arrays.sort(nonDc);
        return nonDc[nonDc.length / 2];
    }

    private static double luminance(int rgb) {
        int red = (rgb >>> 16) & 0xff;
        int green = (rgb >>> 8) & 0xff;
        int blue = rgb & 0xff;
        return (299.0 * red + 587.0 * green + 114.0 * blue) / 1000.0;
    }

    private static double interpolate(double left, double right, double weight) {
        return left + ((right - left) * weight);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double[] createDctScale() {
        double[] scale = new double[LOW_FREQUENCY_SIZE];
        double base = Math.sqrt(2.0 / SAMPLE_SIZE);
        scale[0] = Math.sqrt(1.0 / SAMPLE_SIZE);
        Arrays.fill(scale, 1, scale.length, base);
        return scale;
    }

    private static double[][] createCosineTable() {
        double[][] cosine = new double[SAMPLE_SIZE][LOW_FREQUENCY_SIZE];
        for (int position = 0; position < SAMPLE_SIZE; position++) {
            for (int frequency = 0; frequency < LOW_FREQUENCY_SIZE; frequency++) {
                cosine[position][frequency] = Math.cos(
                        ((2.0 * position + 1.0) * frequency * Math.PI)
                                / (2.0 * SAMPLE_SIZE));
            }
        }
        return cosine;
    }
}
