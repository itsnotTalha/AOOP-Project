package com.authvault.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface Sha256Service {

    Sha256Result calculate(InputStream inputStream) throws IOException;

    Sha256Result copyAndCalculate(
            InputStream inputStream,
            OutputStream outputStream,
            long maxBytes) throws IOException;

    record Sha256Result(String hash, long bytesRead) {
    }
}
