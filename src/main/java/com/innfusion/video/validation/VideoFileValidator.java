package com.innfusion.video.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.web.multipart.MultipartFile;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;

/**
 * Validator implementation for the @VideoFile annotation.
 * 
 * Provides comprehensive validation for uploaded video files including:
 * - File presence and size validation
 * - Content type validation
 * - File extension validation
 * - Security checks
 */
@Slf4j
public class VideoFileValidator implements ConstraintValidator<VideoFile, MultipartFile> {
    
    private long maxSizeBytes;
    private long minSizeBytes;
    private String[] allowedContentTypes;
    private String[] allowedExtensions;
    
    @Override
    public void initialize(VideoFile constraintAnnotation) {
        this.maxSizeBytes = constraintAnnotation.maxSizeBytes();
        this.minSizeBytes = constraintAnnotation.minSizeBytes();
        this.allowedContentTypes = constraintAnnotation.allowedContentTypes();
        this.allowedExtensions = constraintAnnotation.allowedExtensions();
    }
    
    @Override
    public boolean isValid(MultipartFile file, ConstraintValidatorContext context) {
        if (file == null || file.isEmpty()) {
            addViolation(context, "Video file cannot be null or empty");
            return false;
        }
        
        // Validate file size
        if (!isValidFileSize(file, context)) {
            return false;
        }
        
        // Validate content type
        if (!isValidContentType(file, context)) {
            return false;
        }
        
        // Validate file extension
        if (!isValidFileExtension(file, context)) {
            return false;
        }
        
        // Security validation
        if (!isSecureFile(file, context)) {
            return false;
        }
        
        log.debug("Video file validation passed: name={}, size={}, contentType={}", 
                 file.getOriginalFilename(), file.getSize(), file.getContentType());
        
        return true;
    }
    
    private boolean isValidFileSize(MultipartFile file, ConstraintValidatorContext context) {
        long fileSize = file.getSize();
        
        if (fileSize < minSizeBytes) {
            addViolation(context, 
                String.format("File size (%d bytes) is below minimum required size (%d bytes)", 
                             fileSize, minSizeBytes));
            return false;
        }
        
        if (fileSize > maxSizeBytes) {
            addViolation(context, 
                String.format("File size (%d bytes) exceeds maximum allowed size (%d bytes)", 
                             fileSize, maxSizeBytes));
            return false;
        }
        
        return true;
    }
    
    private boolean isValidContentType(MultipartFile file, ConstraintValidatorContext context) {
        String contentType = file.getContentType();
        
        if (contentType == null || contentType.trim().isEmpty()) {
            addViolation(context, "File content type cannot be determined");
            return false;
        }
        
        boolean isAllowed = Arrays.stream(allowedContentTypes)
                                  .anyMatch(allowed -> allowed.equalsIgnoreCase(contentType.trim()));
        
        if (!isAllowed) {
            addViolation(context, 
                String.format("Content type '%s' is not allowed. Allowed types: %s", 
                             contentType, String.join(", ", allowedContentTypes)));
            return false;
        }
        
        return true;
    }
    
    private boolean isValidFileExtension(MultipartFile file, ConstraintValidatorContext context) {
        String fileName = file.getOriginalFilename();
        
        if (fileName == null || fileName.trim().isEmpty()) {
            addViolation(context, "File name cannot be determined");
            return false;
        }
        
        String lowerFileName = fileName.toLowerCase();
        boolean hasValidExtension = Arrays.stream(allowedExtensions)
                                          .anyMatch(ext -> lowerFileName.endsWith(ext.toLowerCase()));
        
        if (!hasValidExtension) {
            addViolation(context, 
                String.format("File extension is not allowed. File: '%s', Allowed extensions: %s", 
                             fileName, String.join(", ", allowedExtensions)));
            return false;
        }
        
        return true;
    }
    
    private boolean isSecureFile(MultipartFile file, ConstraintValidatorContext context) {
        String fileName = file.getOriginalFilename();
        
        if (fileName == null) {
            return true; // Already handled in extension validation
        }
        
        // Security: Check for dangerous file name patterns
        String lowerFileName = fileName.toLowerCase();
        
        // Check for path traversal attempts
        if (lowerFileName.contains("../") || lowerFileName.contains("..\\") || 
            lowerFileName.contains("/") || lowerFileName.contains("\\")) {
            addViolation(context, "File name contains invalid path characters");
            return false;
        }
        
        // Check for executable file extensions that might be disguised
        String[] dangerousExtensions = {".exe", ".bat", ".cmd", ".sh", ".ps1", ".jar", ".com", ".scr"};
        for (String dangerous : dangerousExtensions) {
            if (lowerFileName.contains(dangerous)) {
                addViolation(context, "File name contains potentially dangerous content");
                return false;
            }
        }
        
        return true;
    }
    
    private void addViolation(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
        log.warn("Video file validation failed: {}", message);
    }
} 