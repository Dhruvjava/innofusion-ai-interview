package com.innfusion.openai.service;

import com.innfusion.base.BaseDataRs;
import com.innfusion.video.domain.VideoUploadResult;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.CompletableFuture;

/**
 * Enhanced Video Stream Service with High-Performance Upload Capabilities
 * 
 * Design Patterns Used:
 * - Strategy Pattern: Multiple upload strategies (processing vs direct upload)
 * - Template Method Pattern: Common validation and processing steps
 * - Observer Pattern: Performance monitoring and metrics
 * - Factory Pattern: Upload result creation
 */
public interface VideoStreamService {

    /**
     * Process video stream with AI integration (existing functionality)
     */
    BaseDataRs processVideoStream(MultipartFile file, String question, String intervieweeId, String questionId);

    /**
     * High-performance video upload to S3 optimized for 50MB uploads in <1 second
     * 
     * Converts video to HLS format and uploads HLS files + question text to S3
     * Creates consistent cloud directory structure: s3://bucket/intervieweeId/questionId/
     * Uses same file names as local implementation for consistency
     * 
     * @param file The video file to upload and convert to HLS (max 50MB)
     * @param question The interview question text
     * @param intervieweeId The interviewee identifier
     * @param questionId The question identifier  
     * @return CompletableFuture with detailed performance metrics and upload result
     */
    CompletableFuture<BaseDataRs> uploadVideoToS3(MultipartFile file, String question, String intervieweeId, String questionId);

    /**
     * Complete interview upload to S3 - uploads both video and question text
     * 
     * Creates cloud directory structure matching local storage:
     * s3://bucket/intervieweeId/questionId/
     * ├── timestamp_video.mp4 (video file)
     * └── question.txt (question text)
     * 
     * @param file The video file to upload (max 50MB)
     * @param question The interview question text
     * @param intervieweeId The interviewee identifier
     * @param questionId The question identifier
     * @return CompletableFuture with upload results for both video and question
     */
    CompletableFuture<BaseDataRs> uploadCompleteInterviewToS3(MultipartFile file, String question, String intervieweeId, String questionId);

    /**
     * Legacy method - now delegates to main high-performance implementation
     * 
     * @deprecated Use uploadVideoToS3 which now uses Spring Cloud AWS by default
     */
    @Deprecated(since = "2.0", forRemoval = false)
    CompletableFuture<BaseDataRs> uploadVideoUsingSpringCloudAws(MultipartFile file, String intervieweeId, String questionId);

    /**
     * Get performance metrics and system health status
     * 
     * @return Performance status including average speed, success rate, and recommendations
     */
    BaseDataRs getPerformanceStatus();

    /**
     * Check if file exists in storage
     * 
     * @param intervieweeId The interviewee identifier
     * @param questionId The question identifier
     * @param fileName The file name
     * @return true if file exists, false otherwise
     */
    CompletableFuture<Boolean> checkVideoExists(String intervieweeId, String questionId, String fileName);

    /**
     * Generate signed URL for video access
     * 
     * @param intervieweeId The interviewee identifier
     * @param questionId The question identifier
     * @param fileName The file name
     * @param durationHours URL validity in hours
     * @return Signed URL for video access
     */
    CompletableFuture<String> generateVideoUrl(String intervieweeId, String questionId, String fileName, int durationHours);

    /**
     * Delete video from storage
     * 
     * @param intervieweeId The interviewee identifier
     * @param questionId The question identifier
     * @param fileName The file name
     * @return true if deletion successful, false otherwise
     */
    CompletableFuture<Boolean> deleteVideo(String intervieweeId, String questionId, String fileName);
}
