package com.example.wallet.shared.error;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail notFound(ResourceNotFoundException exception, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, exception.getCode(), exception.getMessage(), request);
    }

    @ExceptionHandler(BusinessRuleException.class)
    ProblemDetail conflict(BusinessRuleException exception, HttpServletRequest request) {
        HttpStatus status = exception.getCode().startsWith("INVALID_")
            ? HttpStatus.BAD_REQUEST : HttpStatus.CONFLICT;
        return problem(status, exception.getCode(), exception.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        String detail = exception.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .orElse("Request validation failed");
        return problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", detail, request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail constraint(DataIntegrityViolationException exception, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "DATA_CONFLICT",
            "The operation conflicts with the current resource state", request);
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    ProblemDetail lockTimeout(PessimisticLockingFailureException exception, HttpServletRequest request) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "DATABASE_BUSY",
            "The resource is busy; retry the request", request);
    }

    @ExceptionHandler(QueryTimeoutException.class)
    ProblemDetail queryTimeout(QueryTimeoutException exception, HttpServletRequest request) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "DATABASE_TIMEOUT",
            "The database operation timed out; retry the request", request);
    }

    private ProblemDetail problem(HttpStatus status, String code, String detail,
                                  HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setType(URI.create("urn:wallet:error:" + code.toLowerCase()));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        problem.setProperty("requestId", MDC.get("requestId"));
        return problem;
    }
}
