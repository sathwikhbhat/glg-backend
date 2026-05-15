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
    void shouldReturn404ForShortKeyNotFoundException() throws Exception {
        mockMvc.perform(get("/test/short-key-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("No link found for key: abc123"))
                .andExpect(jsonPath("$.path").value("/test/short-key-not-found"));
    }

    @Test
    void shouldReturn400ForIllegalArgumentException() throws Exception {
        mockMvc.perform(get("/test/illegal-argument"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Unsupported timeRange '2h'"))
                .andExpect(jsonPath("$.path").value("/test/illegal-argument"));
    }

    @Test
    void shouldReturn404ForResponseStatusException() throws Exception {
        mockMvc.perform(get("/test/response-status"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Invalid URL"))
                .andExpect(jsonPath("$.path").value("/test/response-status"));
    }

    @Test
    void shouldReturn500ForWriterException() throws Exception {
        mockMvc.perform(get("/test/io-exception"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("Failed to generate QR code"))
                .andExpect(jsonPath("$.path").value("/test/io-exception"));
    }

    @Test
    void shouldReturn500ForUnhandledException() throws Exception {
        mockMvc.perform(get("/test/unhandled"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("Internal server error"))
                .andExpect(jsonPath("$.path").value("/test/unhandled"));
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

        @GetMapping("/io-exception")
        String ioException() throws com.google.zxing.WriterException {
            throw new com.google.zxing.WriterException("QR generation failed");
        }

        @GetMapping("/unhandled")
        String unhandled() {
            throw new RuntimeException("Unexpected crash");
        }
    }
}
