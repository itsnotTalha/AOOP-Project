package com.authvault.service;

import com.authvault.dto.originality.OriginalityVerificationResponse;
import org.springframework.web.multipart.MultipartFile;

public interface OriginalityVerificationService {

    OriginalityVerificationResponse verify(MultipartFile image);
}
