package com.golinkgone.glgbackend.exception;

import com.google.zxing.WriterException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ShortKeyNotFoundException.class)
    public ResponseEntity<ApiError> handleShortKeyNotFound(ShortKeyNotFoundException ex) {
        log.debug("Short key not found: {}", ex.getMessage());
        return buildError(HttpStatus.NOT_FOUND, "The requested link does not exist");
    }

    @ExceptionHandler(ShortKeyGenerationException.class)
    public ResponseEntity<ApiError> handleShortKeyGeneration(ShortKeyGenerationException ex) {
        log.error("Short-key generation failed: {}", ex.getMessage());
        return buildError(HttpStatus.SERVICE_UNAVAILABLE, "Could not generate a short link, please retry");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        HttpStatus resolvedStatus = status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR;
        log.debug("Response status exception: {}", ex.getReason());
        return buildError(resolvedStatus, resolvedStatus.getReasonPhrase());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex) {
        log.debug("Illegal argument: {}", ex.getMessage());
        return buildError(HttpStatus.BAD_REQUEST, "Invalid request Parameters");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .findFirst()
                .orElse("Validation failed");
        return buildError(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(WriterException.class)
    public ResponseEntity<ApiError> handleQrException(WriterException ex) {
        log.warn("QR code generation failed", ex);
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to generate QR code");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied() {
        return buildError(HttpStatus.FORBIDDEN, "Access denied");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        log.error("Unhandled exception [{}]: {}", ex.getClass().getSimpleName(), ex.getMessage(), ex);
        Throwable cause = ex.getCause();
        int depth = 0;
        while (cause != null && cause != cause.getCause() && depth < 20) {
            log.error("  caused by [{}]: {}", cause.getClass().getName(), cause.getMessage());
            cause = cause.getCause();
            depth++;
        }
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
    }

    private ResponseEntity<ApiError> buildError(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(new ApiError(
                        Instant.now(),
                        status.value(),
                        status.getReasonPhrase(),
                        message
                ));
    }
}
