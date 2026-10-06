package com.smart.phset.api.feature.ai;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

@RestControllerAdvice(assignableTypes = AiDetectionController.class)
public class AiDetectionErrorHandler {
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<?> invalid(Exception exception) {
        String message = exception instanceof IllegalArgumentException
                ? exception.getMessage() : "Malformed or invalid detection request";
        return ResponseEntity.badRequest().body(Map.of(
                "error", "invalid_detection", "message", message));
    }
}
