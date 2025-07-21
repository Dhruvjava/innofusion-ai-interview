package com.innfusion.exception;

/**
 * Exception thrown when AWS S3 operations fail.
 * Used for upload, download, delete, and other S3 operations.
 */
public class S3UploadException extends RuntimeException {
    
    private final String operation;
    
    public S3UploadException(String message, String operation) {
        super(message);
        this.operation = operation;
    }
    
    public S3UploadException(String message, String operation, Throwable cause) {
        super(message, cause);
        this.operation = operation;
    }
    
    public String getOperation() {
        return operation;
    }
} 