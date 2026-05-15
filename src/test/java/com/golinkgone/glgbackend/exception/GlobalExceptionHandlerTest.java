package com.golinkgone.glgbackend.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shortKeyNotFound_returns404WithCannedMessage() throws Exception {
        mockMvc.perform(get("/test/short-key-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("The requested link does not exist"));
    }

    @Test
    void illegalArgument_returns400WithCannedMessage() throws Exception {
        mockMvc.perform(get("/test/illegal-argument"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Invalid request Parameters"));
    }

    @Test
    void responseStatusException_propagatesStatusCode() throws Exception {
        mockMvc.perform(get("/test/response-status"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    void writerException_returns500WithQrMessage() throws Exception {
        mockMvc.perform(get("/test/writer-exception"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("Failed to generate QR code"));
    }

    @Test
    void unhandledException_returns500WithGenericMessage() throws Exception {
        mockMvc.perform(get("/test/unhandled"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("Internal server error"));
    }

    @Test
    void apiErrorResponse_carriesTimestampField() throws Exception {
        mockMvc.perform(get("/test/unhandled"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @RestController
    @RequestMapping("/test")
    static class TestController {

        @GetMapping("/short-key-not-found")
        String shortKeyNotFound() {
            throw new ShortKeyNotFoundException("No link found for key: abc123");
        }

        @GetMapping("/illegal-argument")
        String illegalArgument() {
            throw new IllegalArgumentException("Unsupported timeRange '2h'");
        }

        @GetMapping("/response-status")
        String responseStatus() {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid URL");
        }

        @GetMapping("/writer-exception")
        String writerException() throws com.google.zxing.WriterException {
            throw new com.google.zxing.WriterException("QR generation failed");
        }

        @GetMapping("/unhandled")
        String unhandled() {
            throw new RuntimeException("Unexpected crash");
        }
    }
}
