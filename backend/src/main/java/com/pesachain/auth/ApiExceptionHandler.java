package com.pesachain.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingServletRequestParameterException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApi(ApiException exception, HttpServletRequest request) {
        return problem(exception.status(), exception.code(), exception.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Check the submitted fields.", request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ProblemDetail> handleMissingParameter(MissingServletRequestParameterException exception, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "A required field is missing.", request);
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String detail, HttpServletRequest request) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setTitle(status.getReasonPhrase());
        body.setType(URI.create("https://pesachain.local/problems/" + code.toLowerCase()));
        body.setProperty("code", code);
        body.setProperty("path", request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
