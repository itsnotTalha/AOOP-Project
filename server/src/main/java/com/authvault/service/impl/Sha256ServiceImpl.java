package com.authvault.service.impl;

import com.authvault.exception.FileSizeLimitExceededException;
import com.authvault.service.Sha256Service;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class Sha256ServiceImpl implements Sha256Service {

    private static final int BUFFER_SIZE = 8192;

    @Override
    public Sha256Result calculate(InputStream inputStream) throws IOException {
        return process(inputStream, null, Long.MAX_VALUE);
    }

    @Override
    public Sha256Result copyAndCalculate(
            InputStream inputStream,
            OutputStream outputStream,
            long maxBytes) throws IOException {
        if (outputStream == null) {
            throw new IllegalArgumentException("Output stream is required");
        }
        if (maxBytes < 0) {
            throw new IllegalArgumentException("Maximum byte count must not be negative");
        }
        return process(inputStream, outputStream, maxBytes);
    }

    private Sha256Result process(
            InputStream inputStream,
            OutputStream outputStream,
            long maxBytes) throws IOException {
        if (inputStream == null) {
            throw new IllegalArgumentException("Input stream is required");
        }

        MessageDigest digest = sha256Digest();
        long bytesReadTotal = 0;
        byte[] buffer = new byte[BUFFER_SIZE];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            if (bytesRead == 0) {
                continue;
            }
            bytesReadTotal += bytesRead;
            if (bytesReadTotal > maxBytes) {
                throw new FileSizeLimitExceededException("File exceeds the configured maximum size");
            }
            digest.update(buffer, 0, bytesRead);
            if (outputStream != null) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }

        return new Sha256Result(
                HexFormat.of().formatHex(digest.digest()),
                bytesReadTotal);
    }

    private MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
