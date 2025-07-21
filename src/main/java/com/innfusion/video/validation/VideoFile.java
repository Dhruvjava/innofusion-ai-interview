package com.innfusion.video.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Custom validation annotation for video file validation.
 * 
 * Validates:
 * - File is not null and not empty
 * - File size is within specified limits
 * - File content type is a valid video type
 * - File has a valid extension
 */
@Documented
@Constraint(validatedBy = VideoFileValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface VideoFile {
    
    String message() default "Invalid video file";
    
    Class<?>[] groups() default {};
    
    Class<? extends Payload>[] payload() default {};
    
    /**
     * Maximum file size in bytes
     */
    long maxSizeBytes() default 52_428_800L; // 50MB default
    
    /**
     * Minimum file size in bytes
     */
    long minSizeBytes() default 1024L; // 1KB minimum
    
    /**
     * Allowed video content types
     */
    String[] allowedContentTypes() default {
        "video/mp4", "video/avi", "video/mov", "video/quicktime",
        "video/x-msvideo", "video/webm", "video/ogg", "video/3gpp"
    };
    
    /**
     * Allowed file extensions
     */
    String[] allowedExtensions() default {
        ".mp4", ".avi", ".mov", ".webm", ".ogg", ".3gp", ".mkv"
    };
} 