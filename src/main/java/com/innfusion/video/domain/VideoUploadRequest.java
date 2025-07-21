package com.innfusion.video.domain;

import com.innfusion.video.validation.VideoFile;
import lombok.Builder;
import lombok.Value;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;

/**
 * Domain object representing a video upload request with comprehensive validation.
 * Uses Bean Validation (JSR-303) for robust input validation.
 * Immutable value object following SOLID principles.
 */
@Value
@Builder
public class VideoUploadRequest {
    
    @NotNull(message = "Video file is required")
    @VideoFile(maxSizeBytes = 52_428_800L, message = "Video file must be valid and under 50MB")
    MultipartFile file;
    
    @NotBlank(message = "Interviewee ID is required and cannot be empty")
    @Size(min = 1, max = 100, message = "Interviewee ID must be between 1 and 100 characters")
    String intervieweeId;
    
    @NotBlank(message = "Question ID is required and cannot be empty") 
    @Size(min = 1, max = 100, message = "Question ID must be between 1 and 100 characters")
    String questionId;
    
    Map<String, String> metadata;
    
    @Builder.Default
    Instant requestTime = Instant.now();
    
    /**
     * Legacy validation method - now handled by Bean Validation annotations
     * @deprecated Use Bean Validation (@Valid) instead
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public void validate() {
        // Bean Validation (@Valid, @VideoFile, @NotBlank, @Size) now handles all validation
        // This method is kept for backward compatibility but should not be used
        System.out.println("WARNING: Using deprecated validate() method. Please use Bean Validation (@Valid) instead.");
    }
    
    /**
     * Generates S3 key for this upload request
     */
    public String generateS3Key() {
        String sanitizedFilename = sanitizeFilename(file.getOriginalFilename());
        String timestamp = String.valueOf(System.currentTimeMillis());
        return String.format("%s/%s/%s_%s", 
            intervieweeId.trim(), 
            questionId.trim(), 
            timestamp,
            sanitizedFilename);
    }
    
    private String sanitizeFilename(String filename) {
        if (filename == null) {
            return "video.mp4";
        }
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
} 