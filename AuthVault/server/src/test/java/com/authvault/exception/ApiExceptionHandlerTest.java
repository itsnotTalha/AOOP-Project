package com.authvault.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class ApiExceptionHandlerTest {
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new Failures())
            .setControllerAdvice(new ApiExceptionHandler()).build();

    @Test
    void domainErrorPreservesStatusAndMessageWithoutExtraFields() throws Exception {
        mvc.perform(get("/domain")).andExpect(status().isConflict())
                .andExpect(content().string("{\"success\":false,\"message\":\"Example conflict\"}"));
    }

    @Test
    void unclassifiedErrorUses500AndOriginalMessage() throws Exception {
        mvc.perform(get("/unexpected")).andExpect(status().isInternalServerError())
                .andExpect(content().string("{\"success\":false,\"message\":\"Example failure\"}"));
    }

    @Test
    void missingErrorMessageUsesLegacyFallback() throws Exception {
        mvc.perform(get("/empty")).andExpect(status().isInternalServerError())
                .andExpect(content().string("{\"success\":false,\"message\":\"Internal Server Error\"}"));
    }

    // Test-only controllers are never scanned into the application.
    @RestController
    @Profile("exception-handler-unit-tests")
    static class Failures {
        @GetMapping("/domain") void domain() { throw new ApiException(409, "Example conflict"); }
        @GetMapping("/unexpected") void unexpected() { throw new IllegalStateException("Example failure"); }
        @GetMapping("/empty") void empty() { throw new IllegalStateException(); }
    }
}
