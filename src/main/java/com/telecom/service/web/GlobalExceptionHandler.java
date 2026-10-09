package com.telecom.service.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** One consistent error shape; never leaks stack traces, SQL or internals. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public record ErrorBody(Instant timestamp, int status, String error, String message, Map<String, String> fieldErrors) {}

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorBody> api(ApiException ex) {
        return build(ex.getStatus().value(), ex.getMessage(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorBody> validation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> fields.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        return build(400, "Validation failed", fields);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorBody> unreadable(HttpMessageNotReadableException ex) {
        return build(400, "Malformed or invalid request body", null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorBody> typeMismatch(MethodArgumentTypeMismatchException ex) {
        return build(400, "Invalid value for parameter '" + ex.getName() + "'", null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ErrorBody> denied(AccessDeniedException ex) {
        return build(403, "Access denied", null);
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ErrorBody> unauthenticated(AuthenticationException ex) {
        return build(401, "Authentication required", null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErrorBody> integrity(DataIntegrityViolationException ex) {
        // Do not log the exception itself: MySQL's message embeds the duplicate value (e.g. an email address).
        log.warn("Data integrity violation ({})", ex.getMostSpecificCause().getClass().getSimpleName());
        return build(409, "The request conflicts with existing data", null);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorBody> other(Exception ex) {
        if (ex instanceof ErrorResponse er) { // standard Spring MVC errors (404 route, 405, 415, ...)
            HttpStatusCode sc = er.getStatusCode();
            HttpStatus hs = HttpStatus.resolve(sc.value());
            return build(sc.value(), hs != null ? hs.getReasonPhrase() : "Request failed", null);
        }
        log.error("Unhandled exception", ex);
        return build(500, "Unexpected server error", null);
    }

    private ResponseEntity<ErrorBody> build(int status, String message, Map<String, String> fields) {
        HttpStatus hs = HttpStatus.resolve(status);
        String error = hs != null ? hs.getReasonPhrase() : "Error";
        return ResponseEntity.status(status).body(new ErrorBody(Instant.now(), status, error, message, fields));
    }
}
