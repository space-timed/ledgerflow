package com.ledgerflow.common.error;

import com.ledgerflow.common.logging.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String PROBLEM_BASE_TYPE = "https://api.ledgerflow.dev/errors/";

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        ErrorCode code = ex.getErrorCode();
        log.warn("Business rule violation: [{}] - {}", code.name(), ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.getHttpStatus(), ex.getMessage());
        problem.setType(URI.create(PROBLEM_BASE_TYPE + code.name().toLowerCase().replace('_', '-')));
        problem.setTitle(code.getDefaultMessage());
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("errorCode", code.name());
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("correlationId", MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID_KEY));

        return ResponseEntity.status(code.getHttpStatus()).body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        Map<String, String> validationErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            validationErrors.put(error.getField(), error.getDefaultMessage());
        }

        String path = (request instanceof ServletWebRequest swr) ? swr.getRequest().getRequestURI() : "/";
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Input validation failed");
        problem.setType(URI.create(PROBLEM_BASE_TYPE + "invalid-payload"));
        problem.setTitle("Validation Failed");
        problem.setInstance(URI.create(path));
        problem.setProperty("errorCode", ErrorCode.INVALID_PAYLOAD.name());
        problem.setProperty("invalidParams", validationErrors);
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("correlationId", MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID_KEY));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgumentException(IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Illegal argument: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setType(URI.create(PROBLEM_BASE_TYPE + "bad-request"));
        problem.setTitle("Bad Request");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("errorCode", ErrorCode.INVALID_PAYLOAD.name());
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("correlationId", MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID_KEY));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception processing request: {}", request.getRequestURI(), ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected server error occurred");
        problem.setType(URI.create(PROBLEM_BASE_TYPE + "internal-error"));
        problem.setTitle("Internal Server Error");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("errorCode", ErrorCode.INTERNAL_SERVER_ERROR.name());
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("correlationId", MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID_KEY));

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }
}
