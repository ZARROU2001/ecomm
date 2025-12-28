package com.perso.ecomm.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@ControllerAdvice
public class DefaultExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(DefaultExceptionHandler.class);

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthenticationException(
            AuthenticationException e,
            HttpServletRequest request) {

        ApiError apiError = new ApiError(
                request.getRequestURI(),
                e.getMessage(),
                HttpStatus.UNAUTHORIZED.value(),
                LocalDateTime.now()
        );

        return new ResponseEntity<>(apiError, HttpStatus.UNAUTHORIZED);
    }



    private ResponseEntity<ApiError> buildResponseEntity(HttpServletRequest request, String message, HttpStatus status) {
        ApiError apiError = new ApiError(
                request.getRequestURI(),
                message,
                status.value(),
                LocalDateTime.now()
        );
        return new ResponseEntity<>(apiError, status);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<String> handleNoResourceFound(NoResourceFoundException ex) {
        // Customize the error response here
        String customErrorMessage = "The requested resource was not found. Please check the URL.";
        return new ResponseEntity<>(customErrorMessage, HttpStatus.NOT_FOUND);
    }


    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleException(ResourceNotFoundException e, HttpServletRequest request) {
        logger.error("Resource not found: {}", e.getMessage());
        return buildResponseEntity(request, e.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ApiError> handleException(InsufficientStockException e, HttpServletRequest request) {
        logger.error("Insufficient stock: {}", e.getMessage());
        return buildResponseEntity(request, e.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiError> handleException(DuplicateResourceException e, HttpServletRequest request) {
        logger.error("Duplicate resource: {}", e.getMessage());
        return buildResponseEntity(request, e.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(RequestValidationException.class)
    public ResponseEntity<ApiError> handleRequestValidationException(RequestValidationException e, HttpServletRequest request) {
        logger.error("Request validation failed: {}", e.getMessage());
        return buildResponseEntity(request, e.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        logger.error("Method argument type mismatch: {}", e.getMessage());
        return buildResponseEntity(request, e.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(value = AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDeniedException(AccessDeniedException e, HttpServletRequest request) {
        logger.error("Access denied: {}", e.getMessage());
        return buildResponseEntity(request, e.getMessage(), HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.toList());

        logger.error("Validation errors: {}", errors);
        ApiError apiError = new ApiError(
                request.getRequestURI(),
                String.join(", ", errors),
                HttpStatus.BAD_REQUEST.value(),
                LocalDateTime.now()
        );
        return new ResponseEntity<>(apiError, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(InsufficientAuthenticationException.class)
    public ResponseEntity<ApiError> handleException(InsufficientAuthenticationException e, HttpServletRequest request) {
        logger.error("Insufficient authentication: {}", e.getMessage());
        return buildResponseEntity(request, e.getMessage(), HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleException(BadCredentialsException e, HttpServletRequest request) {
        logger.error("Bad credentials: {}", e.getMessage());
        return buildResponseEntity(request, e.getMessage(), HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleException(Exception e, HttpServletRequest request) {
        logger.error("Unexpected error occurred: {}", e.getMessage(), e);
        String userFriendlyMessage = "An unexpected error occurred. Please try again later.";
        return buildResponseEntity(request, userFriendlyMessage, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
