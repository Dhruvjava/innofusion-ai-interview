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
     * INSTANT RESPONSE - Fire-and-forget async HLS conversion and upload using Java 21 Virtual Threads
     * API responds in ~100ms, HLS conversion + upload continues in background Virtual Thread
     * 
     * Perfect for user experience: immediate response while heavy work happens asynchronously
     * Process: Video → FFmpeg HLS Conversion → Upload HLS segments + playlist to S3
     * 
     * @param file The video file to upload and convert to HLS (max 50MB)
     * @param question The interview question text
     * @param intervieweeId The interviewee identifier
     * @param questionId The question identifier
     * @return BaseDataRs with operation ID and immediate response (target: <100ms)
     */
    BaseDataRs uploadVideoInstantResponse(MultipartFile file, String question, String intervieweeId, String questionId);

    /**
     * Get upload status for async operations
     * 
     * @param operationId The operation ID returned from instant response upload
     * @return BaseDataRs with upload status and completion details
     */
    BaseDataRs getUploadStatus(String operationId);

}
