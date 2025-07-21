package com.innfusion.video.domain;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.Map;

/**
 * Domain object representing the result of a video upload operation.
 * Immutable value object following SOLID principles.
 */
@Value
@Builder
public class VideoUploadResult {
    
    boolean success;
    String s3Key;
    String bucketName;
    String fileUrl;
    long fileSizeBytes;
    String contentType;
    String intervieweeId;
    String questionId;
    Instant uploadTime;
    long uploadDurationMs;
    String uploadMethod;
    Map<String, Object> additionalData;
    String errorMessage;
    
    /**
     * Creates a successful upload result
     */
    public static VideoUploadResult success(VideoUploadRequest request, String s3Key, 
                                          String bucketName, long uploadDurationMs) {
        return VideoUploadResult.builder()
            .success(true)
            .s3Key(s3Key)
            .bucketName(bucketName)
            .fileSizeBytes(request.getFile().getSize())
            .contentType(request.getFile().getContentType())
            .intervieweeId(request.getIntervieweeId())
            .questionId(request.getQuestionId())
            .uploadTime(Instant.now())
            .uploadDurationMs(uploadDurationMs)
            .uploadMethod("Spring Cloud AWS S3Template")
            .build();
    }
    
    /**
     * Creates a failed upload result
     */
    public static VideoUploadResult failure(VideoUploadRequest request, String errorMessage) {
        return VideoUploadResult.builder()
            .success(false)
            .intervieweeId(request != null ? request.getIntervieweeId() : null)
            .questionId(request != null ? request.getQuestionId() : null)
            .fileSizeBytes(request != null && request.getFile() != null ? request.getFile().getSize() : 0)
            .uploadTime(Instant.now())
            .errorMessage(errorMessage)
            .build();
    }
    
    /**
     * Checks if upload was completed within performance target (1 second)
     */
    public boolean isWithinPerformanceTarget() {
        return success && uploadDurationMs <= 1000;
    }
    
    /**
     * Gets upload speed in MB/s
     */
    public double getUploadSpeedMBps() {
        if (!success || uploadDurationMs <= 0) {
            return 0.0;
        }
        double sizeInMB = fileSizeBytes / (1024.0 * 1024.0);
        double timeInSeconds = uploadDurationMs / 1000.0;
        return sizeInMB / timeInSeconds;
    }
} 