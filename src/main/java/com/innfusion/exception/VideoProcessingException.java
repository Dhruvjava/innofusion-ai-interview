package com.innfusion.exception;

/**
 * Exception thrown when video processing operations fail.
 * Used for FFmpeg operations, video validation, and video conversion errors.
 */
public class VideoProcessingException extends RuntimeException {
    
    private final String processingStage;
    
    public VideoProcessingException(String message, String processingStage) {
        super(message);
        this.processingStage = processingStage;
    }
    
    public VideoProcessingException(String message, String processingStage, Throwable cause) {
        super(message, cause);
        this.processingStage = processingStage;
    }
    
    public String getProcessingStage() {
        return processingStage;
    }
} 