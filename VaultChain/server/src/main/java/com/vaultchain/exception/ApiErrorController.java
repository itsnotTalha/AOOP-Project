package com.vaultchain.exception;

import com.vaultchain.dto.ErrorResponse;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Servlet error dispatches must not fall back to Boot's HTML/ProblemDetail response. */
@RestController
public class ApiErrorController implements ErrorController {
    @RequestMapping("/error")
    public ResponseEntity<ErrorResponse> error(HttpServletRequest request) {
        Object statusAttribute = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = statusAttribute instanceof Integer value ? value : 404;
        String message = status == 404 ? "Route not found"
                : (String) request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
        return ApiExceptionHandler.response(status, message);
    }
}
