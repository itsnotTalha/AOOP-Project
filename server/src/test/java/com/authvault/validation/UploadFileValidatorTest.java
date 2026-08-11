package com.authvault.validation;

import com.authvault.config.UploadProperties;
import com.authvault.entity.DigitalAsset;
import com.authvault.exception.BadRequestException;
import com.authvault.exception.FileSizeLimitExceededException;
import com.authvault.exception.MalformedFileException;
import com.authvault.exception.UnsupportedFileTypeException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UploadFileValidatorTest {

    private UploadProperties uploadProperties;
    private UploadFileValidator validator;

    @BeforeEach
    void setUp() {
        uploadProperties = new UploadProperties();
        validator = new UploadFileValidator(uploadProperties);
    }

    @Test
    void acceptsDecodableJpegAndSanitizesTraversalFilenameWithoutTrustingMimeType() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "../../private\\portrait.JPEG",
                "application/pdf",
                createImage("jpg"));

        ValidatedUploadFile result = validator.validate(file, DigitalAsset.AssetType.IMAGE);

        assertEquals("portrait.jpeg", result.displayFilename());
        assertEquals("jpg", result.extension());
        assertEquals("image/jpeg", result.mimeType());
        assertFalse(result.displayFilename().contains(".."));
        assertFalse(result.displayFilename().contains("/"));
        assertFalse(result.displayFilename().contains("\\"));
    }

    @Test
    void acceptsDecodablePng() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "diagram.png", "text/plain", createImage("png"));

        ValidatedUploadFile result = validator.validate(file, DigitalAsset.AssetType.IMAGE);

        assertEquals("diagram.png", result.displayFilename());
        assertEquals("png", result.extension());
        assertEquals("image/png", result.mimeType());
    }

    @Test
    void acceptsParseablePdfWithoutTrustingMimeType() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "report.pdf", "image/png", createPdf());

        ValidatedUploadFile result = validator.validate(file, DigitalAsset.AssetType.DOCUMENT);

        assertEquals("report.pdf", result.displayFilename());
        assertEquals("application/pdf", result.mimeType());
    }

    @Test
    void rejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]);

        assertThrows(BadRequestException.class,
                () -> validator.validate(file, DigitalAsset.AssetType.IMAGE));
    }

    @Test
    void enforcesConfiguredImageLimit() {
        uploadProperties.setImageMaxSize(DataSize.ofBytes(10));
        MockMultipartFile file = sizedFile("large.jpg", 11);

        assertThrows(FileSizeLimitExceededException.class,
                () -> validator.validate(file, DigitalAsset.AssetType.IMAGE));
    }

    @Test
    void enforcesConfiguredDocumentLimit() {
        uploadProperties.setDocumentMaxSize(DataSize.ofBytes(20));
        MockMultipartFile file = sizedFile("large.pdf", 21);

        assertThrows(FileSizeLimitExceededException.class,
                () -> validator.validate(file, DigitalAsset.AssetType.DOCUMENT));
    }

    @Test
    void rejectsUnsupportedExtension() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "image.webp", "image/webp", new byte[]{1, 2, 3});

        assertThrows(UnsupportedFileTypeException.class,
                () -> validator.validate(file, DigitalAsset.AssetType.IMAGE));
    }

    @Test
    void rejectsSignatureThatDoesNotMatchExtension() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "image.png", "image/png", createImage("jpg"));

        assertThrows(UnsupportedFileTypeException.class,
                () -> validator.validate(file, DigitalAsset.AssetType.IMAGE));
    }

    @Test
    void rejectsCorruptJpegWithValidSignature() {
        byte[] corruptJpeg = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00, 0x01};
        MockMultipartFile file = new MockMultipartFile(
                "file", "corrupt.jpg", "image/jpeg", corruptJpeg);

        assertThrows(MalformedFileException.class,
                () -> validator.validate(file, DigitalAsset.AssetType.IMAGE));
    }

    @Test
    void rejectsCorruptPngWithValidSignature() {
        byte[] corruptPng = {
                (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
        };
        MockMultipartFile file = new MockMultipartFile(
                "file", "corrupt.png", "image/png", corruptPng);

        assertThrows(MalformedFileException.class,
                () -> validator.validate(file, DigitalAsset.AssetType.IMAGE));
    }

    @Test
    void rejectsMalformedPdfWithValidSignature() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "corrupt.pdf", "application/pdf", "%PDF-1.7\nnot-a-pdf".getBytes());

        assertThrows(MalformedFileException.class,
                () -> validator.validate(file, DigitalAsset.AssetType.DOCUMENT));
    }

    @Test
    void defaultsMatchMvpSizeLimits() {
        assertEquals(DataSize.ofMegabytes(25), uploadProperties.getImageMaxSize());
        assertEquals(DataSize.ofMegabytes(50), uploadProperties.getDocumentMaxSize());
    }

    private MockMultipartFile sizedFile(String filename, int size) {
        return new MockMultipartFile("file", filename, "application/octet-stream", new byte[size]);
    }

    private byte[] createImage(String format) throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.BLUE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.dispose();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, format, output)) {
            throw new IllegalStateException("Test image format is unavailable: " + format);
        }
        return output.toByteArray();
    }

    private byte[] createPdf() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.save(output);
        }
        return output.toByteArray();
    }
}
