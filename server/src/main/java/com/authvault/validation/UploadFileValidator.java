package com.authvault.validation;

import com.authvault.config.UploadProperties;
import com.authvault.entity.DigitalAsset;
import com.authvault.exception.BadRequestException;
import com.authvault.exception.FileSizeLimitExceededException;
import com.authvault.exception.MalformedFileException;
import com.authvault.exception.UnsupportedFileTypeException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.util.Locale;

@Component
public class UploadFileValidator {

    private static final byte[] JPEG_SIGNATURE = {(byte) 0xff, (byte) 0xd8, (byte) 0xff};
    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
    };
    private static final byte[] PDF_SIGNATURE = {0x25, 0x50, 0x44, 0x46, 0x2d};
    private static final int MAX_DISPLAY_FILENAME_LENGTH = 255;

    private final UploadProperties uploadProperties;

    public UploadFileValidator(UploadProperties uploadProperties) {
        this.uploadProperties = uploadProperties;
    }

    public ValidatedUploadFile validate(MultipartFile file, DigitalAsset.AssetType assetType) {
        PreparedUploadFile preparedFile = prepare(file, assetType);
        Path temporaryFile = null;
        try {
            temporaryFile = Files.createTempFile("authvault-upload-validation-", "." + preparedFile.extension());
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, temporaryFile, StandardCopyOption.REPLACE_EXISTING);
            }
            return validateTempFile(temporaryFile, preparedFile, Files.size(temporaryFile));
        } catch (BadRequestException | FileSizeLimitExceededException | UnsupportedFileTypeException
                 | MalformedFileException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new MalformedFileException("Uploaded file could not be read", exception);
        } finally {
            deleteTemporaryFileQuietly(temporaryFile);
        }
    }

    public PreparedUploadFile prepare(MultipartFile file, DigitalAsset.AssetType assetType) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Uploaded file is required and must not be empty");
        }
        if (assetType == null) {
            throw new BadRequestException("Asset type is required");
        }

        long maxSize = maxSizeFor(assetType);
        if (file.getSize() > maxSize) {
            throw new FileSizeLimitExceededException(
                    assetType + " file exceeds the configured maximum size of " + maxSize + " bytes");
        }

        SanitizedFilename filename = sanitizeFilename(file.getOriginalFilename());
        SupportedFormat expectedFormat = formatFor(assetType, filename.extension());

        return new PreparedUploadFile(
                filename.displayName(),
                expectedFormat.extension,
                assetType,
                maxSize);
    }

    public ValidatedUploadFile validateTempFile(
            Path temporaryFile,
            PreparedUploadFile preparedFile,
            long actualFileSize) {
        if (temporaryFile == null || preparedFile == null) {
            throw new BadRequestException("Validated temporary upload is required");
        }
        if (actualFileSize <= 0) {
            throw new BadRequestException("Uploaded file is required and must not be empty");
        }
        if (actualFileSize > preparedFile.maxFileSizeBytes()) {
            throw new FileSizeLimitExceededException(
                    preparedFile.assetType() + " file exceeds the configured maximum size of "
                            + preparedFile.maxFileSizeBytes() + " bytes");
        }

        SupportedFormat expectedFormat = formatFor(preparedFile.assetType(), preparedFile.extension());
        ensureReadableSize(temporaryFile, actualFileSize);
        ensureSignatureMatches(temporaryFile, expectedFormat);
        ensureParseable(temporaryFile, expectedFormat);

        return new ValidatedUploadFile(
                preparedFile.displayFilename(),
                expectedFormat.extension,
                expectedFormat.mimeType,
                actualFileSize);
    }

    private long maxSizeFor(DigitalAsset.AssetType assetType) {
        return switch (assetType) {
            case IMAGE -> uploadProperties.getImageMaxSize().toBytes();
            case DOCUMENT -> uploadProperties.getDocumentMaxSize().toBytes();
        };
    }

    private SanitizedFilename sanitizeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new UnsupportedFileTypeException("Original filename must include a supported extension");
        }

        String normalized = Normalizer.normalize(originalFilename, Normalizer.Form.NFKC)
                .replace('\\', '/');
        String basename = normalized.substring(normalized.lastIndexOf('/') + 1)
                .replaceAll("[\\p{Cntrl}]", "")
                .trim();

        int extensionSeparator = basename.lastIndexOf('.');
        if (extensionSeparator <= 0 || extensionSeparator == basename.length() - 1) {
            throw new UnsupportedFileTypeException("Original filename must include a supported extension");
        }

        String extension = basename.substring(extensionSeparator + 1).toLowerCase(Locale.ROOT);
        if (!extension.matches("[a-z0-9]+")) {
            throw new UnsupportedFileTypeException("File extension is invalid");
        }

        String stem = basename.substring(0, extensionSeparator)
                .replaceAll("\\.{2,}", "_")
                .replaceAll("[^\\p{L}\\p{N}._() -]", "_")
                .replaceFirst("^\\.+", "")
                .trim();
        if (stem.isBlank()) {
            stem = "upload";
        }

        int maxStemLength = MAX_DISPLAY_FILENAME_LENGTH - extension.length() - 1;
        if (maxStemLength <= 0) {
            throw new UnsupportedFileTypeException("File extension is too long");
        }
        if (stem.length() > maxStemLength) {
            stem = stem.substring(0, maxStemLength).trim();
        }

        return new SanitizedFilename(stem + "." + extension, extension);
    }

    private SupportedFormat formatFor(DigitalAsset.AssetType assetType, String extension) {
        return switch (assetType) {
            case IMAGE -> switch (extension) {
                case "jpg", "jpeg" -> SupportedFormat.JPEG;
                case "png" -> SupportedFormat.PNG;
                default -> throw new UnsupportedFileTypeException(
                        "Images must use a .jpg, .jpeg, or .png extension");
            };
            case DOCUMENT -> {
                if (!"pdf".equals(extension)) {
                    throw new UnsupportedFileTypeException("Documents must use a .pdf extension");
                }
                yield SupportedFormat.PDF;
            }
        };
    }

    private void ensureReadableSize(Path temporaryFile, long expectedSize) {
        try {
            if (!Files.isRegularFile(temporaryFile) || Files.size(temporaryFile) != expectedSize) {
                throw new MalformedFileException("Uploaded file is malformed or incomplete");
            }
        } catch (MalformedFileException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new MalformedFileException("Uploaded file could not be read", exception);
        }
    }

    private void ensureSignatureMatches(Path temporaryFile, SupportedFormat expectedFormat) {
        try (InputStream inputStream = Files.newInputStream(temporaryFile)) {
            byte[] header = inputStream.readNBytes(PNG_SIGNATURE.length);
            if (!startsWith(header, expectedFormat.signature)) {
                throw new UnsupportedFileTypeException(
                        "File content does not match its " + expectedFormat.displayName + " extension");
            }
        } catch (UnsupportedFileTypeException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new MalformedFileException("Uploaded file could not be read", exception);
        }
    }

    private void ensureParseable(Path temporaryFile, SupportedFormat expectedFormat) {
        switch (expectedFormat) {
            case JPEG, PNG -> ensureImageParseable(temporaryFile, expectedFormat);
            case PDF -> ensurePdfParseable(temporaryFile);
        }
    }

    private void ensureImageParseable(Path temporaryFile, SupportedFormat format) {
        try (InputStream inputStream = Files.newInputStream(temporaryFile)) {
            BufferedImage image = ImageIO.read(inputStream);
            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
                throw new MalformedFileException(format.displayName + " file is malformed or corrupt");
            }
        } catch (MalformedFileException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new MalformedFileException(format.displayName + " file is malformed or corrupt", exception);
        }
    }

    private void ensurePdfParseable(Path temporaryFile) {
        try {
            try (PDDocument ignored = Loader.loadPDF(temporaryFile.toFile())) {
                // Successful loading is sufficient; OCR or text extraction is outside this MVP.
            }
        } catch (IOException | RuntimeException exception) {
            throw new MalformedFileException("PDF file is malformed or corrupt", exception);
        }
    }

    private void deleteTemporaryFileQuietly(Path temporaryFile) {
        if (temporaryFile == null) {
            return;
        }
        try {
            Files.deleteIfExists(temporaryFile);
        } catch (IOException ignored) {
            // Compatibility validation cleanup must not mask the validation result.
        }
    }

    private boolean startsWith(byte[] content, byte[] signature) {
        if (content.length < signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if (content[index] != signature[index]) {
                return false;
            }
        }
        return true;
    }

    private record SanitizedFilename(String displayName, String extension) {
    }

    private enum SupportedFormat {
        JPEG("jpg", "image/jpeg", "JPEG", JPEG_SIGNATURE),
        PNG("png", "image/png", "PNG", PNG_SIGNATURE),
        PDF("pdf", "application/pdf", "PDF", PDF_SIGNATURE);

        private final String extension;
        private final String mimeType;
        private final String displayName;
        private final byte[] signature;

        SupportedFormat(String extension, String mimeType, String displayName, byte[] signature) {
            this.extension = extension;
            this.mimeType = mimeType;
            this.displayName = displayName;
            this.signature = signature;
        }
    }
}
