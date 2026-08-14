package com.authvault.service;

import java.io.InputStream;

public interface PerceptualHashService {

    String calculate(InputStream imageStream);

    int hammingDistance(String hashA, String hashB);

    boolean isValidHash(String hash);
}
