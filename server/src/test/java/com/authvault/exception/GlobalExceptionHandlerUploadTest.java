package com.authvault.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerUploadTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/assets/images");

    @Test
    void mapsUploadValidationExceptionsToRequiredHttpStatuses() {
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                handler.handleUnsupportedFileType(
                        new UnsupportedFileTypeException("unsupported"), request).getStatusCode());
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE,
                handler.handleFileSizeLimitExceeded(
                        new FileSizeLimitExceededException("too large"), request).getStatusCode());
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY,
                handler.handleMalformedFile(
                        new MalformedFileException("malformed"), request).getStatusCode());
    }

    @Test
    void mapsDuplicateFileToConflictWithStableErrorCode() {
        var response = handler.handleDuplicateFile(new DuplicateFileException(), request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(DuplicateFileException.CODE, response.getBody().getCode());
    }
}
