package com.innfusion.exception;

import com.innfusion.base.BaseDataRs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Global Exception Handler for centralized error management.
 * 
 * Handles:
 * - Bean Validation errors (@Valid, @VideoFile, etc.)
 * - File upload errors
 * - Business logic exceptions
 * - System errors
 * 
 * Provides consistent error response format across all endpoints.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    /**
     * Handle Bean Validation errors from @Valid annotations
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<BaseDataRs> handleValidationExceptions(
            MethodArgumentNotValidException ex, WebRequest request) {
        
        log.warn("Bean validation failed for request: {}", request.getDescription(false));
        
        Map<String, Object> errorDetails = new HashMap<>();
        List<String> errorMessages = new ArrayList<>();
        
        // Collect field-specific validation errors
        Map<String, List<String>> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.groupingBy(
                    FieldError::getField,
                    Collectors.mapping(FieldError::getDefaultMessage, Collectors.toList())
                ));
        
        fieldErrors.forEach((field, messages) -> {
            String fieldMessage = String.format("%s: %s", field, String.join(", ", messages));
            errorMessages.add(fieldMessage);
        });
        
        // Collect global validation errors
        ex.getBindingResult().getGlobalErrors().forEach(error -> {
            errorMessages.add(error.getDefaultMessage());
        });
        
        errorDetails.put("validationErrors", fieldErrors);
        errorDetails.put("errorType", "VALIDATION_ERROR");
        errorDetails.put("totalErrors", errorMessages.size());
        
        String mainMessage = errorMessages.isEmpty() 
            ? "Validation failed"
            : "Validation failed: " + String.join("; ", errorMessages);
        
        BaseDataRs response = new BaseDataRs(mainMessage, errorDetails);
        
        return ResponseEntity.badRequest().body(response);
    }
    
    /**
     * Handle constraint violation exceptions (direct validation calls)
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<BaseDataRs> handleConstraintViolationException(
            ConstraintViolationException ex, WebRequest request) {
        
        log.warn("Constraint validation failed for request: {}", request.getDescription(false));
        
        List<String> errorMessages = ex.getConstraintViolations()
                .stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toList());
        
        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("constraintViolations", errorMessages);
        errorDetails.put("errorType", "CONSTRAINT_VIOLATION");
        errorDetails.put("totalViolations", errorMessages.size());
        
        String mainMessage = "Constraint validation failed: " + String.join("; ", errorMessages);
        BaseDataRs response = new BaseDataRs(mainMessage, errorDetails);
        
        return ResponseEntity.badRequest().body(response);
    }
    
    /**
     * Handle file upload size exceeded errors
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<BaseDataRs> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException ex, WebRequest request) {
        
        log.warn("File upload size exceeded for request: {}", request.getDescription(false));
        
        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("errorType", "FILE_SIZE_EXCEEDED");
        errorDetails.put("maxSize", ex.getMaxUploadSize());
        errorDetails.put("suggestion", "Please reduce file size or compress the video");
        
        String message = String.format("File size exceeds maximum allowed size (%d bytes)", 
                                     ex.getMaxUploadSize());
        
        BaseDataRs response = new BaseDataRs(message, errorDetails);
        
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(response);
    }
    
    /**
     * Handle illegal argument exceptions (business logic validation)
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<BaseDataRs> handleIllegalArgumentException(
            IllegalArgumentException ex, WebRequest request) {
        
        log.warn("Invalid argument for request {}: {}", request.getDescription(false), ex.getMessage());
        
        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("errorType", "INVALID_ARGUMENT");
        errorDetails.put("argument", ex.getMessage());
        
        BaseDataRs response = new BaseDataRs("Invalid input: " + ex.getMessage(), errorDetails);
        
        return ResponseEntity.badRequest().body(response);
    }
    
    /**
     * Handle video processing specific errors
     */
    @ExceptionHandler(VideoProcessingException.class)
    public ResponseEntity<BaseDataRs> handleVideoProcessingException(
            VideoProcessingException ex, WebRequest request) {
        
        log.error("Video processing failed for request {}: {}", 
                 request.getDescription(false), ex.getMessage());
        
        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("errorType", "VIDEO_PROCESSING_ERROR");
        errorDetails.put("processingStage", ex.getProcessingStage());
        errorDetails.put("suggestion", "Please try with a different video file or contact support");
        
        BaseDataRs response = new BaseDataRs("Video processing failed: " + ex.getMessage(), errorDetails);
        
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
    }
    
    /**
     * Handle AWS S3 related errors
     */
    @ExceptionHandler(S3UploadException.class)
    public ResponseEntity<BaseDataRs> handleS3UploadException(
            S3UploadException ex, WebRequest request) {
        
        log.error("S3 upload failed for request {}: {}", 
                 request.getDescription(false), ex.getMessage());
        
        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("errorType", "S3_UPLOAD_ERROR");
        errorDetails.put("s3Operation", ex.getOperation());
        errorDetails.put("suggestion", "Please try again or contact support if problem persists");
        
        BaseDataRs response = new BaseDataRs("Cloud storage error: " + ex.getMessage(), errorDetails);
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }
    
    /**
     * Handle performance monitoring exceptions
     */
    @ExceptionHandler(PerformanceException.class)
    public ResponseEntity<BaseDataRs> handlePerformanceException(
            PerformanceException ex, WebRequest request) {
        
        log.warn("Performance issue for request {}: {}", 
                request.getDescription(false), ex.getMessage());
        
        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("errorType", "PERFORMANCE_WARNING");
        errorDetails.put("performanceMetric", ex.getMetric());
        errorDetails.put("expectedValue", ex.getExpectedValue());
        errorDetails.put("actualValue", ex.getActualValue());
        
        BaseDataRs response = new BaseDataRs("Performance warning: " + ex.getMessage(), errorDetails);
        
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
    
    /**
     * Handle all other runtime exceptions
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<BaseDataRs> handleRuntimeException(
            RuntimeException ex, WebRequest request) {
        
        log.error("Unexpected error for request {}: {}", 
                 request.getDescription(false), ex.getMessage(), ex);
        
        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("errorType", "SYSTEM_ERROR");
        errorDetails.put("timestamp", new Date());
        errorDetails.put("suggestion", "Please contact support with this error information");
        
        BaseDataRs response = new BaseDataRs("System error occurred", errorDetails);
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
    
    /**
     * Handle all other general exceptions
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<BaseDataRs> handleGeneralException(
            Exception ex, WebRequest request) {
        
        log.error("Unexpected error for request {}: {}", 
                 request.getDescription(false), ex.getMessage(), ex);
        
        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("errorType", "GENERAL_ERROR");
        errorDetails.put("exceptionClass", ex.getClass().getSimpleName());
        errorDetails.put("timestamp", new Date());
        
        BaseDataRs response = new BaseDataRs("An unexpected error occurred", errorDetails);
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
} 