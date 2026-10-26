package com.routinemachine.core.api;

import com.routinemachine.core.service.MpesaStatementParseException;
import com.routinemachine.core.service.MpesaStatementPasswordRequiredException;
import com.routinemachine.core.service.NotACohortParticipantException;
import com.routinemachine.core.service.NotAnOrganisationAdminException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        error -> error.getField(),
                        error -> error.getDefaultMessage() == null ? "invalid" : error.getDefaultMessage(),
                        (first, second) -> first));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not found"));
    }

    // E.g. GranaryService.storeSaving before a saving target amount has been set — a real,
    // client-fixable precondition failure, not a server bug.
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }

    // A distinct "type" from a plain parse failure, so the frontend can prompt for a password
    // specifically rather than showing a generic "couldn't read this statement" error.
    @ExceptionHandler(MpesaStatementPasswordRequiredException.class)
    public ResponseEntity<Map<String, String>> handlePasswordRequired(MpesaStatementPasswordRequiredException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage(), "type", "PASSWORD_REQUIRED"));
    }

    @ExceptionHandler(MpesaStatementParseException.class)
    public ResponseEntity<Map<String, String>> handleMpesaParseFailure(MpesaStatementParseException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage(), "type", "PARSE_ERROR"));
    }

    @ExceptionHandler(NotAnOrganisationAdminException.class)
    public ResponseEntity<Map<String, String>> handleNotAnOrganisationAdmin(NotAnOrganisationAdminException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(NotACohortParticipantException.class)
    public ResponseEntity<Map<String, String>> handleNotACohortParticipant(NotACohortParticipantException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", ex.getMessage()));
    }
}
