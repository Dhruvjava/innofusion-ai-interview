package com.innfusion.openai.rest;

import com.innfusion.base.BaseDataRs;
import com.innfusion.openai.service.VideoStreamService;

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
 * - Instant response upload with Virtual Threads
 * - Background upload status tracking
 * - Original video processing with AI integration (legacy)
 */
@RestController
@RequestMapping("/api/v1/video")
@RequiredArgsConstructor
@Slf4j
@Validated
public class VideoStreamRest {

    private final VideoStreamService service;

    /**
     * Original video processing endpoint with AI integration (legacy)
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
     * INSTANT RESPONSE upload endpoint - API responds in ~100ms, HLS conversion + upload continues in Virtual Thread
     * Perfect for user experience: immediate response while heavy work happens asynchronously
     * 
     * Process: Video → FFmpeg HLS Conversion → Upload HLS segments + playlist to S3
     */
    @PostMapping("/upload-instant")
    public ResponseEntity<BaseDataRs> uploadVideoInstantResponse(
            @RequestParam("file") MultipartFile file,
            @RequestParam("question") @NotBlank @Size(min = 1, max = 5000) String question,
            @RequestParam("intervieweeId") @NotBlank @Size(min = 1, max = 100) String intervieweeId,
            @RequestParam("questionId") @NotBlank @Size(min = 1, max = 100) String questionId) {
        
        // Pre-validation using utility methods
        ValidationUtils.validateUploadParameters(intervieweeId, questionId, file, "instant-response-upload");
        
        log.info("🚀⚡💫 INSTANT RESPONSE upload request: size={}MB, target response time <100ms", 
               file.getSize() / (1024.0 * 1024.0));
        
        // SYNCHRONOUS call that returns immediately (~100ms) while upload continues in background
        BaseDataRs result = service.uploadVideoInstantResponse(file, question, intervieweeId, questionId);
        
        if (result.getMessage().contains("SUCCESS")) {
            log.info("✅⚡💫 INSTANT RESPONSE completed - upload continuing in Virtual Thread background");
            return ResponseEntity.ok(result);
        } else {
            log.warn("⚠️ Instant response upload initiation failed: {}", result.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * Check upload status for async operations
     */
    @GetMapping("/upload-status/{operationId}")
    public ResponseEntity<BaseDataRs> getUploadStatus(@PathVariable String operationId) {
        log.debug("Checking upload status for operation: {}", operationId);
        
        BaseDataRs status = service.getUploadStatus(operationId);
        
        if (status.getMessage().contains("not found")) {
            return ResponseEntity.notFound().build();
        } else {
            return ResponseEntity.ok(status);
        }
    }
}
