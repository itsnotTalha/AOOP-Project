package com.authvault.validation;

public record ValidatedUploadFile(
        String displayFilename,
        String extension,
        String mimeType,
        long fileSize) {
}
