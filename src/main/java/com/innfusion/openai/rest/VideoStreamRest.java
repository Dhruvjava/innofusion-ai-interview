package com.innfusion.openai.rest;

import com.innfusion.base.BaseDataRs;
import com.innfusion.openai.service.VideoStreamService;
import com.innfusion.video.domain.VideoUploadRequest;
import com.innfusion.video.validation.ValidationUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.concurrent.CompletableFuture;

/**
 * Enhanced Video Stream REST Controller with High-Performance Upload Capabilities
 * 
 * Design Patterns Used:
 * - Controller Pattern: REST API facade over business logic
 * - Adapter Pattern: Converts between REST and service layer formats
 * - Strategy Pattern: Different endpoints for different upload strategies
 * 
 * Features:
 * - Original video processing with AI integration
 * - High-performance S3 uploads (50MB in <1 second target)
 * - Performance monitoring and metrics
 * - File management operations (exists, delete, URL generation)
 * - Real-time performance status
 */
@RestController
@RequestMapping("/api/v1/video")
@RequiredArgsConstructor
@Slf4j
@Validated
public class VideoStreamRest {

    private final VideoStreamService service;

    /**
     * Original video processing endpoint with AI integration
     */
    @PostMapping("/process")
    public ResponseEntity<BaseDataRs> processVideo(
            @RequestParam MultipartFile file,
            @RequestParam String intervieweeId,
            @RequestParam String questionId,
            @RequestParam String question) {
        
        log.info("Processing video with AI integration: size={}MB, interviewee={}", 
               file.getSize() / (1024.0 * 1024.0), intervieweeId);
               
        return ResponseEntity.ok(service.processVideoStream(file, question, intervieweeId, questionId));
    }

    /**
     * Legacy endpoint - maintains backward compatibility
     */
    @PostMapping
    public ResponseEntity<BaseDataRs> uploadVideo(
            @RequestParam MultipartFile file,
            @RequestParam String intervieweeId,
            @RequestParam String questionId,
            @RequestParam String question) {
        
        return processVideo(file, intervieweeId, questionId, question);
    }

    /**
     * High-performance video upload endpoint with HLS conversion and comprehensive validation
     * Target: 50MB upload + HLS conversion in under 1000ms
     */
    @PostMapping("/upload")
    public CompletableFuture<ResponseEntity<BaseDataRs>> uploadVideoHighPerformance(
            @RequestParam("file") MultipartFile file,
            @RequestParam("question") @NotBlank @Size(min = 1, max = 5000) String question,
            @RequestParam("intervieweeId") @NotBlank @Size(min = 1, max = 100) String intervieweeId,
            @RequestParam("questionId") @NotBlank @Size(min = 1, max = 100) String questionId) {
        
        // Pre-validation using utility methods
        ValidationUtils.validateUploadParameters(intervieweeId, questionId, file, "high-performance-hls-upload");
        
        log.info("🚀 HLS upload request: size={}MB, question={}chars, interviewee={}, questionId={}", 
               file.getSize() / (1024.0 * 1024.0), question.length(), intervieweeId, questionId);
        
        return service.uploadVideoToS3(file, question, intervieweeId, questionId)
                .thenApply(result -> {
                    if (result.getMessage().contains("SUCCESS")) {
                        log.info("✅ High-performance upload completed successfully");
                        return ResponseEntity.ok(result);
                    } else {
                        log.warn("⚠️ Upload completed with warnings: {}", result.getMessage());
                        return ResponseEntity.ok(result);
                    }
                })
                .exceptionally(throwable -> {
                    log.error("💥 High-performance upload failed: {}", throwable.getMessage());
                    BaseDataRs errorResponse = new BaseDataRs("Upload failed: " + throwable.getMessage());
                    return ResponseEntity.badRequest().body(errorResponse);
                });
    }

    /**
     * Complete interview upload endpoint - uploads both video and question to S3
     * Creates consistent cloud directory structure: bucket/intervieweeId/questionId/
     * Target: 50MB upload in under 1000ms with question text included
     */
    @PostMapping("/upload-complete")
    public CompletableFuture<ResponseEntity<BaseDataRs>> uploadCompleteInterview(
            @RequestParam("file") MultipartFile file,
            @RequestParam("question") @NotBlank @Size(min = 1, max = 5000) String question,
            @RequestParam("intervieweeId") @NotBlank @Size(min = 1, max = 100) String intervieweeId,
            @RequestParam("questionId") @NotBlank @Size(min = 1, max = 100) String questionId) {
        
        // Pre-validation using utility methods
        ValidationUtils.validateUploadParameters(intervieweeId, questionId, file, "complete-interview-upload");
        
        log.info("🚀 Complete interview upload request: size={}MB, question={}chars, interviewee={}, question={}", 
               file.getSize() / (1024.0 * 1024.0), question.length(), intervieweeId, questionId);
        
        return service.uploadCompleteInterviewToS3(file, question, intervieweeId, questionId)
                .thenApply(result -> {
                    if (result.getMessage().contains("SUCCESS")) {
                        log.info("✅ Complete interview upload successful with cloud directory structure");
                        return ResponseEntity.ok(result);
                    } else {
                        log.warn("⚠️ Complete interview upload completed with warnings: {}", result.getMessage());
                        return ResponseEntity.ok(result);
                    }
                })
                .exceptionally(throwable -> {
                    log.error("💥 Complete interview upload failed: {}", throwable.getMessage());
                    BaseDataRs errorResponse = new BaseDataRs("Complete interview upload failed: " + throwable.getMessage());
                    return ResponseEntity.badRequest().body(errorResponse);
                });
    }

    /**
     * Get system performance metrics and health status
     */
    @GetMapping("/performance/status")
    public ResponseEntity<BaseDataRs> getPerformanceStatus() {
        log.debug("Performance status requested");
        BaseDataRs status = service.getPerformanceStatus();
        return ResponseEntity.ok(status);
    }

    /**
     * Check if a video file exists in storage
     */
    @GetMapping("/exists")
    public CompletableFuture<ResponseEntity<Boolean>> checkVideoExists(
            @RequestParam String intervieweeId,
            @RequestParam String questionId,
            @RequestParam String fileName) {
        
        return service.checkVideoExists(intervieweeId, questionId, fileName)
                .thenApply(ResponseEntity::ok)
                .exceptionally(throwable -> {
                    log.error("Error checking video existence: {}", throwable.getMessage());
                    return ResponseEntity.badRequest().body(false);
                });
    }

    /**
     * Generate signed URL for video access
     */
    @GetMapping("/url")
    public CompletableFuture<ResponseEntity<String>> generateVideoUrl(
            @RequestParam String intervieweeId,
            @RequestParam String questionId,
            @RequestParam String fileName,
            @RequestParam(defaultValue = "1") int durationHours) {
        
        return service.generateVideoUrl(intervieweeId, questionId, fileName, durationHours)
                .thenApply(url -> {
                    if (url != null) {
                        return ResponseEntity.ok(url);
                    } else {
                        return ResponseEntity.notFound().<String>build();
                    }
                })
                .exceptionally(throwable -> {
                    log.error("Error generating video URL: {}", throwable.getMessage());
                    return ResponseEntity.badRequest().build();
                });
    }

    /**
     * Delete video from storage
     */
    @DeleteMapping("/delete")
    public CompletableFuture<ResponseEntity<BaseDataRs>> deleteVideo(
            @RequestParam String intervieweeId,
            @RequestParam String questionId,
            @RequestParam String fileName) {
        
        log.info("Delete video requested: interviewee={}, question={}, file={}", 
               intervieweeId, questionId, fileName);
        
        return service.deleteVideo(intervieweeId, questionId, fileName)
                .thenApply(success -> {
                    if (success) {
                        BaseDataRs response = new BaseDataRs("Video deleted successfully");
                        return ResponseEntity.ok(response);
                    } else {
                        BaseDataRs response = new BaseDataRs("Video deletion failed");
                        return ResponseEntity.badRequest().body(response);
                    }
                })
                .exceptionally(throwable -> {
                    log.error("Error deleting video: {}", throwable.getMessage());
                    BaseDataRs errorResponse = new BaseDataRs("Delete operation failed: " + throwable.getMessage());
                    return ResponseEntity.badRequest().body(errorResponse);
                });
    }

    /**
     * Batch upload multiple videos (demonstrates extensible design)
     */
    @PostMapping("/batch-upload")
    public CompletableFuture<ResponseEntity<BaseDataRs>> batchUploadVideos(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam("intervieweeId") String intervieweeId,
            @RequestParam("questionIds") String[] questionIds) {
        
        if (files.length != questionIds.length) {
            BaseDataRs error = new BaseDataRs("Number of files must match number of question IDs");
            return CompletableFuture.completedFuture(ResponseEntity.badRequest().body(error));
        }
        
        log.info("🚀 Batch upload requested: {} files for interviewee {}", files.length, intervieweeId);
        
        // Strategy Pattern: Parallel processing for batch operations
        CompletableFuture<BaseDataRs>[] uploadFutures = new CompletableFuture[files.length];
        
        for (int i = 0; i < files.length; i++) {
            // For batch upload, we don't have individual question texts, so use question ID as placeholder
            String questionPlaceholder = "Question " + questionIds[i];
            uploadFutures[i] = service.uploadVideoToS3(files[i], questionPlaceholder, intervieweeId, questionIds[i]);
        }
        
        return CompletableFuture.allOf(uploadFutures)
                .thenApply(ignored -> {
                    java.util.Map<String, Object> batchResult = new java.util.HashMap<>();
                    batchResult.put("totalFiles", files.length);
                    batchResult.put("intervieweeId", intervieweeId);
                    batchResult.put("batchId", java.util.UUID.randomUUID().toString());
                    
                    // Collect individual results
                    java.util.List<BaseDataRs> results = new java.util.ArrayList<>();
                    for (CompletableFuture<BaseDataRs> future : uploadFutures) {
                        try {
                            results.add(future.get());
                        } catch (Exception e) {
                            results.add(new BaseDataRs("Upload failed: " + e.getMessage()));
                        }
                    }
                    batchResult.put("results", results);
                    
                    BaseDataRs batchResponse = new BaseDataRs("Batch upload completed", batchResult);
                    log.info("✅ Batch upload completed: {} files processed", files.length);
                    
                    return ResponseEntity.ok(batchResponse);
                })
                .exceptionally(throwable -> {
                    log.error("💥 Batch upload failed: {}", throwable.getMessage());
                    BaseDataRs errorResponse = new BaseDataRs("Batch upload failed: " + throwable.getMessage());
                    return ResponseEntity.badRequest().body(errorResponse);
                });
    }
}
