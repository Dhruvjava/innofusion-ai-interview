package com.innfusion.video.validation;

import com.innfusion.exception.VideoProcessingException;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utility class for common validation operations.
 * 
 * Provides reusable validation methods for:
 * - File validation
 * - Bean validation
 * - Business logic validation
 * - Security validation
 */
@UtilityClass
@Slf4j
public class ValidationUtils {
    
    /**
     * Validate an object using Bean Validation and throw exception if invalid
     */
    public static <T> void validateAndThrow(T object, Validator validator) {
        Set<ConstraintViolation<T>> violations = validator.validate(object);
        
        if (!violations.isEmpty()) {
            String errorMessage = violations.stream()
                    .map(ConstraintViolation::getMessage)
                    .collect(Collectors.joining("; "));
            
            log.warn("Validation failed for {}: {}", object.getClass().getSimpleName(), errorMessage);
            throw new IllegalArgumentException("Validation failed: " + errorMessage);
        }
        
        log.debug("Validation passed for {}", object.getClass().getSimpleName());
    }
    
    /**
     * Quick validation for video file basic properties
     */
    public static void validateVideoFileBasics(MultipartFile file, String operation) {
        if (file == null || file.isEmpty()) {
            throw new VideoProcessingException("Video file cannot be null or empty", operation);
        }
        
        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new VideoProcessingException("Video file name cannot be determined", operation);
        }
        
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("video/")) {
            throw new VideoProcessingException(
                String.format("Invalid content type '%s' - expected video content", contentType), 
                operation);
        }
        
        log.debug("Basic video file validation passed: name={}, size={}, contentType={}", 
                 fileName, file.getSize(), contentType);
    }
    
    /**
     * Validate file size constraints
     */
    public static void validateFileSize(MultipartFile file, long minSize, long maxSize, String operation) {
        long fileSize = file.getSize();
        
        if (fileSize < minSize) {
            throw new VideoProcessingException(
                String.format("File size (%d bytes) is below minimum required (%d bytes)", 
                             fileSize, minSize), operation);
        }
        
        if (fileSize > maxSize) {
            throw new VideoProcessingException(
                String.format("File size (%d bytes) exceeds maximum allowed (%d bytes)", 
                             fileSize, maxSize), operation);
        }
        
        log.debug("File size validation passed: {} bytes (min: {}, max: {})", 
                 fileSize, minSize, maxSize);
    }
    
    /**
     * Validate string parameters (not null, not empty, within length limits)
     */
    public static void validateStringParameter(String value, String paramName, 
                                             int minLength, int maxLength) {
        if (value == null) {
            throw new IllegalArgumentException(paramName + " cannot be null");
        }
        
        String trimmedValue = value.trim();
        if (trimmedValue.isEmpty()) {
            throw new IllegalArgumentException(paramName + " cannot be empty");
        }
        
        if (trimmedValue.length() < minLength) {
            throw new IllegalArgumentException(
                String.format("%s must be at least %d characters (actual: %d)", 
                             paramName, minLength, trimmedValue.length()));
        }
        
        if (trimmedValue.length() > maxLength) {
            throw new IllegalArgumentException(
                String.format("%s must not exceed %d characters (actual: %d)", 
                             paramName, maxLength, trimmedValue.length()));
        }
        
        log.debug("String parameter validation passed: {}={} (length: {})", 
                 paramName, trimmedValue, trimmedValue.length());
    }
    
    /**
     * Validate video file extension and content type combination
     */
    public static void validateVideoFileType(MultipartFile file, String[] allowedExtensions, 
                                           String[] allowedContentTypes, String operation) {
        String fileName = file.getOriginalFilename();
        String contentType = file.getContentType();
        
        if (fileName == null || contentType == null) {
            throw new VideoProcessingException("Cannot determine file type information", operation);
        }
        
        // Validate extension
        String lowerFileName = fileName.toLowerCase();
        boolean hasValidExtension = false;
        for (String ext : allowedExtensions) {
            if (lowerFileName.endsWith(ext.toLowerCase())) {
                hasValidExtension = true;
                break;
            }
        }
        
        if (!hasValidExtension) {
            throw new VideoProcessingException(
                String.format("File extension not allowed. File: '%s', Allowed: %s", 
                             fileName, String.join(", ", allowedExtensions)), operation);
        }
        
        // Validate content type
        boolean hasValidContentType = false;
        for (String type : allowedContentTypes) {
            if (type.equalsIgnoreCase(contentType)) {
                hasValidContentType = true;
                break;
            }
        }
        
        if (!hasValidContentType) {
            throw new VideoProcessingException(
                String.format("Content type not allowed. Type: '%s', Allowed: %s", 
                             contentType, String.join(", ", allowedContentTypes)), operation);
        }
        
        log.debug("Video file type validation passed: name={}, contentType={}", fileName, contentType);
    }
    
    /**
     * Security validation for file names
     */
    public static void validateFileNameSecurity(String fileName, String operation) {
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new VideoProcessingException("File name cannot be null or empty", operation);
        }
        
        String lowerFileName = fileName.toLowerCase();
        
        // Check for path traversal
        if (lowerFileName.contains("../") || lowerFileName.contains("..\\")) {
            throw new VideoProcessingException("File name contains path traversal characters", operation);
        }
        
        // Check for directory separators
        if (lowerFileName.contains("/") || lowerFileName.contains("\\")) {
            throw new VideoProcessingException("File name contains directory separators", operation);
        }
        
        // Check for dangerous extensions
        String[] dangerousExtensions = {".exe", ".bat", ".cmd", ".sh", ".ps1", ".jar", ".com", ".scr"};
        for (String dangerous : dangerousExtensions) {
            if (lowerFileName.contains(dangerous)) {
                throw new VideoProcessingException(
                    "File name contains potentially dangerous content: " + dangerous, operation);
            }
        }
        
        log.debug("File name security validation passed: {}", fileName);
    }
    
    /**
     * Validate upload request parameters for completeness
     */
    public static void validateUploadParameters(String intervieweeId, String questionId, 
                                              MultipartFile file, String operation) {
        validateStringParameter(intervieweeId, "intervieweeId", 1, 100);
        validateStringParameter(questionId, "questionId", 1, 100);
        validateVideoFileBasics(file, operation);
        validateFileNameSecurity(file.getOriginalFilename(), operation);
        
        log.debug("Upload parameters validation passed: intervieweeId={}, questionId={}, file={}", 
                 intervieweeId, questionId, file.getOriginalFilename());
    }
    
    /**
     * Check if validation errors exist in a set of violations
     */
    public static <T> boolean hasValidationErrors(Set<ConstraintViolation<T>> violations) {
        return violations != null && !violations.isEmpty();
    }
    
    /**
     * Format validation errors into a readable string
     */
    public static <T> String formatValidationErrors(Set<ConstraintViolation<T>> violations) {
        if (violations == null || violations.isEmpty()) {
            return "No validation errors";
        }
        
        return violations.stream()
                .map(violation -> String.format("%s: %s", 
                    violation.getPropertyPath(), violation.getMessage()))
                .collect(Collectors.joining("; "));
    }
} 