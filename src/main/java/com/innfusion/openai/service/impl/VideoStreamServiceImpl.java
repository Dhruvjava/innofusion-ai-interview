package com.innfusion.openai.service.impl;

import com.github.kokorin.jaffree.ffmpeg.FFmpeg;
import com.github.kokorin.jaffree.ffmpeg.UrlInput;
import com.github.kokorin.jaffree.ffmpeg.UrlOutput;

import io.awspring.cloud.s3.S3Template;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.Executor;
import com.innfusion.base.BaseDataRs;
import com.innfusion.openai.constants.MessageCodes;
import com.innfusion.openai.service.VideoStreamService;
import com.innfusion.utils.Messages;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.DirectoryStream;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.FileVisitResult;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacpp.Loader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
@RequiredArgsConstructor
public class VideoStreamServiceImpl implements VideoStreamService {

    @Value("${innfusion.video.stream.base-dir}")
    private String baseDir;

    @Value("${innfusion.video.stream.tmp-vide-filename}")
    private String tmpVidFilename;

    @Value("${innfusion.video.stream.question-filename}")
    private String queFilename;

    @Value("${innfusion.video.stream.hls-format-dir}")
    private String hlsDir;

    @Value("${innfusion.video.stream.hls-playlist-filename}")
    private String hlsPlayFilename;

    @Value("${innfusion.video.stream.hls-segment-pattern}")
    private String hlsSegPattern;

    // PERFORMANCE OPTIMIZATION SETTINGS
    @Value("${innfusion.video.ffmpeg.preset:ultrafast}")
    private String ffmpegPreset;
    
    @Value("${innfusion.video.ffmpeg.crf:28}")
    private String ffmpegCrf;
    
    @Value("${innfusion.video.ffmpeg.segment-time:6}")
    private String hlsSegmentTime;
    
    @Value("${innfusion.video.ffmpeg.hardware-acceleration:true}")
    private boolean enableHardwareAcceleration;

    private String ffmpegExecutable;

    private final Messages messages;
    private final S3Template s3Template;
    private final Executor videoUploadExecutor;
    
    @Value("${aws.s3.bucket-name}")
    private String s3BucketName;
    
    // Performance monitoring using Observer Pattern
    private final ConcurrentHashMap<String, UploadMetrics> activeUploads = new ConcurrentHashMap<>();
    private final AtomicLong totalUploads = new AtomicLong(0);
    private final AtomicLong successfulUploads = new AtomicLong(0);
    private final DoubleAdder totalUploadTime = new DoubleAdder();
    private final DoubleAdder totalDataTransferred = new DoubleAdder();
    
    // Performance threshold for logging
    private static final long PERFORMANCE_TARGET_MS = 1000; // 1 second

//    public VideoStreamServiceImpl(Messages messages) {
//        this.messages = messages;
//        ffmpegExecutable = Loader.load(org.bytedeco.ffmpeg.global.avutil.class, "ffmpeg");
//    }

    private void loadFfmpeg() {
        if (this.ffmpegExecutable == null) {
            this.ffmpegExecutable = Loader.load(org.bytedeco.ffmpeg.ffmpeg.class);;
        }
    }

    /**
     * Process the uploaded video and question String: - save question as text, - converts video to
     * hls, - save both in structured directory.
     *
     * @param file          The uploaded vide file
     * @param question      The question String
     * @param intervieweeId The Interviewee Id
     * @param questionId    The Question Id
     * @throws
     */

    @Override
    public BaseDataRs processVideoStream(MultipartFile file, String question, String intervieweeId,
                    String questionId) {
        if (log.isDebugEnabled()) {
            log.debug("Executing processVideoStream(MultipartFile , String, String,String) ->");
        }
        
        // Test S3 connectivity first
        try {
            testS3Connectivity();
        } catch (Exception e) {
            log.error("❌ S3 connectivity test failed: {}", e.getMessage());
            return new BaseDataRs("S3 connectivity failed: " + e.getMessage());
        }
        
        try {
            if (file == null || file.isEmpty()) {
                log.error("File is Empty/Null");
                throw new RuntimeException("File is Empty/Null");
            }
            if (!StringUtils.hasText(question)) {
                log.error("Question is Empty/Null");
                throw new RuntimeException("Question is Empty/Null");
            }
            if (!StringUtils.hasText(intervieweeId)) {
                throw new RuntimeException("Interviewee ID is null or blank.");
            }
            if (!StringUtils.hasText(questionId)) {
                throw new RuntimeException("Question ID is null or blank.");
            }
            
            log.info("🎬 Processing video file: {} for intervieweeId: {} questionId: {}", 
                    file.getOriginalFilename(), intervieweeId, questionId);
            
            final Path intervieweeDir = Paths.get(baseDir, intervieweeId);
            final Path questionDir = intervieweeDir.resolve(questionId);
            // Create Directory if not exists
            Files.createDirectories(questionDir);

            // save Question Text file
            Path questionFile = questionDir.resolve(queFilename);
            Files.writeString(questionFile, question, StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING);
            log.info("📝 Question file saved: {}", questionFile);

            // Save Video Temporarily
            Path tempVideoPath = questionDir.resolve(tmpVidFilename);
            try (InputStream is = file.getInputStream()) {
                Files.copy(is, tempVideoPath, StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("🎥 Temporary video saved: {}", tempVideoPath);

            // Create Output Dir for HLS Segment and Playlist
            Path hldOutputDir = questionDir.resolve(hlsDir);
            Files.createDirectories(hldOutputDir);
            log.info("📁 HLS output directory created: {}", hldOutputDir);

            loadFfmpeg();
            log.info("🔧 FFmpeg loaded: {}", ffmpegExecutable);

            Path ffmpegDir = Paths.get(ffmpegExecutable).getParent(); // ✅ get directory only
            
            // Execute OPTIMIZED HLS conversion using centralized high-performance method
            try {
                long ffmpegDuration = executeOptimizedHlsConversion(
                    tempVideoPath, 
                    hldOutputDir.resolve(hlsPlayFilename),
                    hldOutputDir,
                    hldOutputDir.resolve(hlsSegPattern).toString()
                );
                log.info("🎯 Local HLS processing completed in {}ms", ffmpegDuration);
            } catch (Exception ffmpegException) {
                log.error("FFmpeg HLS conversion failed: {}", ffmpegException.getMessage());
                throw new RuntimeException("HLS conversion failed: " + ffmpegException.getMessage(), ffmpegException);
            }
            
            // Delete Temp file after conversion
            Files.deleteIfExists(tempVideoPath);
            log.info("🧹 Temporary video file deleted");

            String message = messages.getMessage(MessageCodes.MC_UPLOADED_SUCCESSFULLY);
            log.info("✅ Video processing completed successfully for intervieweeId: {} questionId: {}", 
                    intervieweeId, questionId);
            
            return new BaseDataRs(message);
        } catch (IOException ioException) {
            log.error("Exception in processVideoStream(MultipartFile , String, String,String) -> {}",
                            ioException.getLocalizedMessage());
            return new BaseDataRs("IO Error during video processing: " + ioException.getMessage());
        } catch (Exception e) {
            log.error("Exception in processVideoStream(MultipartFile , String, String,String) -> {}",
                            e.getLocalizedMessage());
            return new BaseDataRs("Error during video processing: " + e.getMessage());
        }
    }






    



    

    
    /**
     * Extract file extension from filename
     */
    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "mp4";  // default
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }



    // Performance monitoring methods (Observer Pattern implementation)
    private void recordUploadStart(String operationId, long fileSizeBytes) {
        UploadMetrics metrics = new UploadMetrics(operationId, fileSizeBytes, System.currentTimeMillis());
        activeUploads.put(operationId, metrics);
    }
    
    private void recordUploadCompletion(String operationId, boolean success, long durationMs) {
        UploadMetrics metrics = activeUploads.remove(operationId);
        if (metrics == null) return;
        
        totalUploads.incrementAndGet();
        totalUploadTime.add(durationMs);
        
        if (success) {
            successfulUploads.incrementAndGet();
            totalDataTransferred.add(metrics.fileSizeBytes);
        }
    }

    /**
     * OPTIMIZED HLS conversion using high-performance FFmpeg settings
     * Significantly reduces conversion time for any video size
     */
    private long executeOptimizedHlsConversion(Path inputVideo, Path outputPlaylist, Path hlsOutputDir, String segmentPattern) throws Exception {
        long startTime = System.currentTimeMillis();
        
        loadFfmpeg();
        Path ffmpegDir = Paths.get(ffmpegExecutable).getParent();
        
        log.info("🚀 Starting OPTIMIZED HLS conversion with preset: {}, CRF: {}, segment time: {}s", 
                 ffmpegPreset, ffmpegCrf, hlsSegmentTime);
        log.info("📂 Input video: {}", inputVideo);
        log.info("📂 Output playlist: {}", outputPlaylist);
        log.info("📂 HLS output dir: {}", hlsOutputDir);
        
        var outputBuilder = UrlOutput.toPath(outputPlaylist)
                // SPEED OPTIMIZATIONS - Configurable:
                .addArguments("-preset", ffmpegPreset)              // ultrafast/superfast/veryfast
                .addArguments("-crf", ffmpegCrf)                    // 18-28 (lower=quality, higher=speed)
                .addArguments("-threads", "0")                      // Use all CPU cores
                
                // HLS OPTIMIZATIONS - Configurable:
                .addArguments("-start_number", "0")
                .addArguments("-hls_time", hlsSegmentTime)          // Configurable segment duration
                .addArguments("-hls_list_size", "0")
                .addArguments("-f", "hls")
                .addArguments("-hls_flags", "independent_segments") // Enable parallel processing
                .addArguments("-hls_segment_filename", segmentPattern)
                
                // STREAMING OPTIMIZATIONS:
                .addArguments("-movflags", "+faststart")            // Fast start for web
                .addArguments("-avoid_negative_ts", "disabled");    // Skip timestamp adjustments
        
        // VIDEO ENCODER SELECTION - Use available encoders
        if (enableHardwareAcceleration) {
            log.debug("🎯 Hardware acceleration ENABLED - trying videotoolbox for macOS");
            try {
                // Use videotoolbox for macOS
                outputBuilder.addArguments("-c:v", "h264_videotoolbox")
                           .addArguments("-allow_sw", "1")           // Allow fallback to software
                           .addArguments("-b:v", "2M");              // Set bitrate for hardware encoding
                log.debug("✅ Hardware encoding configured: h264_videotoolbox");
            } catch (Exception e) {
                log.warn("⚠️ Hardware acceleration failed, falling back to available software encoder: {}", e.getMessage());
                // Try available software encoders in order of preference
                outputBuilder.addArguments("-c:v", "libopenh264");  // Available in bundled FFmpeg
            }
        } else {
            log.debug("🎯 Using software encoding with available encoder");
            // Use available software encoders - libopenh264 is available in the bundled FFmpeg
            outputBuilder.addArguments("-c:v", "libopenh264")
                        .addArguments("-profile:v", "baseline")     // Compatible profile
                        .addArguments("-level", "3.0");            // Compatible level
        }
        
        // Audio encoding - use available AAC encoder
        outputBuilder.addArguments("-c:a", "aac")
                    .addArguments("-b:a", "128k");                  // Audio bitrate
        
        try {
            log.info("🎬 Executing FFmpeg command...");
            FFmpeg.atPath(ffmpegDir).addInput(UrlInput.fromPath(inputVideo))
                    .addOutput(outputBuilder)
                    .execute();
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("⚡ OPTIMIZED HLS conversion completed in {}ms (preset: {}, hardware accel: {})", 
                     duration, ffmpegPreset, enableHardwareAcceleration ? "enabled" : "disabled");
            
            return duration;
        } catch (Exception e) {
            log.error("💥 FFmpeg execution failed: {}", e.getMessage());
            log.error("🔍 FFmpeg executable path: {}", ffmpegExecutable);
            log.error("🔍 FFmpeg directory: {}", ffmpegDir);
            log.error("🔍 Input video exists: {}", Files.exists(inputVideo));
            log.error("🔍 Output directory exists: {}", Files.exists(hlsOutputDir));
            
            // Try fallback with copy codec (no re-encoding)
            log.warn("🔄 Trying fallback with copy codec (no re-encoding)...");
            try {
                var fallbackBuilder = UrlOutput.toPath(outputPlaylist)
                        .addArguments("-c", "copy")                     // Copy streams without re-encoding
                        .addArguments("-start_number", "0")
                        .addArguments("-hls_time", hlsSegmentTime)
                        .addArguments("-hls_list_size", "0")
                        .addArguments("-f", "hls")
                        .addArguments("-hls_flags", "independent_segments")
                        .addArguments("-hls_segment_filename", segmentPattern);
                
                FFmpeg.atPath(ffmpegDir).addInput(UrlInput.fromPath(inputVideo))
                        .addOutput(fallbackBuilder)
                        .execute();
                
                long fallbackDuration = System.currentTimeMillis() - startTime;
                log.info("✅ Fallback HLS conversion completed in {}ms using copy codec", fallbackDuration);
                return fallbackDuration;
                
            } catch (Exception fallbackException) {
                log.error("💥 Fallback conversion also failed: {}", fallbackException.getMessage());
                throw new RuntimeException("FFmpeg HLS conversion failed with all methods: " + e.getMessage() + 
                                         ". Fallback error: " + fallbackException.getMessage(), e);
            }
        }
    }

    /**
     * Recursively delete a directory and all its contents
     */
    private void deleteDirectoryRecursively(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        
        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
            
            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Internal class for tracking upload metrics (part of Observer Pattern)
     */
    private static class UploadMetrics {
        final String operationId;
        final long fileSizeBytes;
        final long startTimeMs;
        
        UploadMetrics(String operationId, long fileSizeBytes, long startTimeMs) {
            this.operationId = operationId;
            this.fileSizeBytes = fileSizeBytes;
            this.startTimeMs = startTimeMs;
        }
    }

    /**
     * Test S3 connectivity before attempting uploads
     */
    private void testS3Connectivity() {
        try {
            log.info("🔍 Testing S3 connectivity to bucket: {}", s3BucketName);
            // Try to list objects in bucket (this will fail if credentials/bucket are wrong)
            s3Template.listObjects(s3BucketName, "test/");
            log.info("✅ S3 connectivity test successful");
        } catch (Exception e) {
            log.error("❌ S3 connectivity test failed - bucket: {}, error: {}", s3BucketName, e.getMessage());
            throw new RuntimeException("S3 connectivity failed: " + e.getMessage(), e);
        }
    }



    /**
     * INSTANT RESPONSE - Fire-and-forget async HLS conversion and upload using Java 21 Virtual Threads
     * API responds in ~100ms, HLS conversion + upload continues in background Virtual Thread
     * Target: API response <100ms, HLS processing happens asynchronously
     * 
     * Process: Video → FFmpeg HLS Conversion → Upload HLS segments + playlist to S3
     */
    public BaseDataRs uploadVideoInstantResponse(MultipartFile file, String question, String intervieweeId, String questionId) {
        String operationId = "instant-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        long startTime = System.currentTimeMillis();
        
        try {
            // IMMEDIATE VALIDATION (fast)
            if (file == null || file.isEmpty()) {
                throw new RuntimeException("File is Empty/Null");
            }
            if (!StringUtils.hasText(question)) {
                throw new RuntimeException("Question is Empty/Null");
            }
            if (!StringUtils.hasText(intervieweeId)) {
                throw new RuntimeException("Interviewee ID is null or blank");
            }
            if (!StringUtils.hasText(questionId)) {
                throw new RuntimeException("Question ID is null or blank");
            }
            
            log.info("🚀⚡💫 INSTANT RESPONSE upload started: operation={}, size={}MB", 
                    operationId, file.getSize() / (1024.0 * 1024.0));
            
            // FIRE-AND-FORGET: Start async upload in Virtual Thread (non-blocking)
            CompletableFuture.runAsync(() -> {
                performAsyncVirtualThreadUpload(file, question, intervieweeId, questionId, operationId);
            }, videoUploadExecutor);
            
            long responseTime = System.currentTimeMillis() - startTime;
            
            log.info("✅⚡ INSTANT API RESPONSE in {}ms - upload continuing in Virtual Thread", responseTime);
            
            // IMMEDIATE RESPONSE (target: <100ms)
            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("uploadMethod", "INSTANT_RESPONSE_VIRTUAL_THREAD");
            data.put("operationId", operationId);
            data.put("status", "UPLOADING_IN_BACKGROUND");
            data.put("responseTimeMs", responseTime);
            data.put("fileSizeMB", file.getSize() / (1024.0 * 1024.0));
            data.put("estimatedUploadTimeMs", "500-1000");
            data.put("virtualThreadsEnabled", true);
            data.put("asyncUploadStarted", true);
            data.put("checkStatusUrl", "/api/v1/video/upload-status/" + operationId);
            
            String message = String.format("🚀⚡ INSTANT SUCCESS: API responded in %dms, %.1fMB upload started in Virtual Thread", 
                responseTime, file.getSize() / (1024.0 * 1024.0));
            
            return new BaseDataRs(message, data);
            
        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            log.error("💥 INSTANT RESPONSE failed in {}ms: {}", responseTime, e.getMessage());
            return new BaseDataRs("Instant upload initiation failed: " + e.getMessage());
        }
    }
    


    /**
     * Async Virtual Thread upload with HLS conversion (runs in background)
     */
    private void performAsyncVirtualThreadUpload(MultipartFile file, String question, String intervieweeId, String questionId, String operationId) {
        long uploadStartTime = System.currentTimeMillis();
        Path tempDir = null;
        
        try {
            log.info("🚀🎬 VIRTUAL THREAD HLS conversion and S3 upload started: operation={}", operationId);
            recordUploadStart(operationId, file.getSize());
            
            // Create base S3 directory structure: intervieweeId/questionId/
            String baseS3Key = String.format("%s/%s", intervieweeId.trim(), questionId.trim());
            
            // Create temporary directory for HLS processing
            tempDir = Files.createTempDirectory("hls-instant-upload-" + operationId);
            
            // Save video file temporarily
            Path tempVideoPath = tempDir.resolve(tmpVidFilename);
            try (InputStream is = file.getInputStream()) {
                Files.copy(is, tempVideoPath, StandardCopyOption.REPLACE_EXISTING);
            }
            log.debug("📹 Temporary video saved: {}", tempVideoPath);
            
            // Create question text file
            Path questionFile = tempDir.resolve(queFilename);
            Files.writeString(questionFile, question, StandardOpenOption.CREATE);
            log.debug("📝 Question file created: {}", questionFile);
            
            // Create HLS output directory
            Path hlsOutputDir = tempDir.resolve(hlsDir);
            Files.createDirectories(hlsOutputDir);
            
            log.info("🎬 Converting video to HLS format using OPTIMIZED processing...");
            
            // Execute OPTIMIZED HLS conversion
            long ffmpegDuration = executeOptimizedHlsConversion(
                tempVideoPath, 
                hlsOutputDir.resolve(hlsPlayFilename),
                hlsOutputDir,
                hlsOutputDir.resolve(hlsSegPattern).toString()
            );
            
            log.info("✅ HLS conversion completed in {}ms, uploading to S3...", ffmpegDuration);
            
            // Upload question text file to S3
            String questionS3Key = String.format("%s/%s", baseS3Key, queFilename);
            s3Template.upload(s3BucketName, questionS3Key, Files.newInputStream(questionFile));
            log.debug("📝 Question uploaded: {}", questionS3Key);
            
            // Upload HLS playlist file to S3
            String playlistS3Key = String.format("%s/%s/%s", baseS3Key, hlsDir, hlsPlayFilename);
            Path playlistFile = hlsOutputDir.resolve(hlsPlayFilename);
            s3Template.upload(s3BucketName, playlistS3Key, Files.newInputStream(playlistFile));
            log.debug("🎵 HLS playlist uploaded: {}", playlistS3Key);
            
            // Upload all HLS segment files to S3
            int segmentCount = 0;
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(hlsOutputDir, "*.ts")) {
                for (Path segmentFile : stream) {
                    String segmentS3Key = String.format("%s/%s/%s", baseS3Key, hlsDir, segmentFile.getFileName());
                    s3Template.upload(s3BucketName, segmentS3Key, Files.newInputStream(segmentFile));
                    segmentCount++;
                }
            }
            log.info("📺 HLS segments uploaded: {} segments", segmentCount);
            
            // Delete temporary video file
            Files.deleteIfExists(tempVideoPath);
            
            long uploadDuration = System.currentTimeMillis() - uploadStartTime;
            recordUploadCompletion(operationId, true, uploadDuration);
            
            double speedMBps = (file.getSize() / (1024.0 * 1024.0)) / (uploadDuration / 1000.0);
            
            log.info("✅🎬 VIRTUAL THREAD HLS upload COMPLETED: operation={}, duration={}ms, speed={:.2f}MB/s, segments={}", 
                     operationId, uploadDuration, speedMBps, segmentCount);
            
            // Store upload result for status checking
            storeUploadResult(operationId, true, uploadDuration, speedMBps, playlistS3Key);
            
        } catch (Exception e) {
            long uploadDuration = System.currentTimeMillis() - uploadStartTime;
            recordUploadCompletion(operationId, false, uploadDuration);
            
            log.error("💥 VIRTUAL THREAD HLS upload FAILED: operation={}, duration={}ms, error={}", 
                     operationId, uploadDuration, e.getMessage());
            
            // Store failure result
            storeUploadResult(operationId, false, uploadDuration, 0.0, null);
        } finally {
            // Clean up temporary directory
            if (tempDir != null) {
                try {
                    deleteDirectoryRecursively(tempDir);
                    log.debug("🧹 Temporary directory cleaned up: {}", tempDir);
                } catch (Exception e) {
                    log.warn("⚠️ Failed to clean up temporary directory {}: {}", tempDir, e.getMessage());
                }
            }
        }
    }
    
    /**
     * Store upload result for status checking (using ConcurrentHashMap for thread safety)
     */
    private final ConcurrentHashMap<String, UploadResult> uploadResults = new ConcurrentHashMap<>();
    
    private void storeUploadResult(String operationId, boolean success, long duration, double speedMBps, String s3Key) {
        UploadResult result = new UploadResult(
            operationId, success, duration, speedMBps, s3Key, System.currentTimeMillis()
        );
        uploadResults.put(operationId, result);
        
        // Clean up old results (keep only last 1000)
        if (uploadResults.size() > 1000) {
            String oldestKey = uploadResults.keySet().iterator().next();
            uploadResults.remove(oldestKey);
        }
    }
    
    /**
     * Get upload status for async operations
     */
    public BaseDataRs getUploadStatus(String operationId) {
        UploadResult result = uploadResults.get(operationId);
        
        if (result == null) {
            return new BaseDataRs("Upload operation not found: " + operationId);
        }
        
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("operationId", operationId);
        data.put("success", result.success);
        data.put("uploadDurationMs", result.duration);
        data.put("uploadSpeedMBps", String.format("%.2f", result.speedMBps));
        data.put("s3Key", result.s3Key);
        data.put("completedAt", new java.util.Date(result.completedAt));
        data.put("virtualThreadProcessing", true);
        
        String message = result.success 
            ? String.format("✅ Upload completed successfully in %dms (%.2f MB/s)", result.duration, result.speedMBps)
            : String.format("❌ Upload failed after %dms", result.duration);
            
        return new BaseDataRs(message, data);
    }
    
    /**
     * Internal class for storing upload results
     */
    private static class UploadResult {
        final String operationId;
        final boolean success;
        final long duration;
        final double speedMBps;
        final String s3Key;
        final long completedAt;
        
        UploadResult(String operationId, boolean success, long duration, double speedMBps, String s3Key, long completedAt) {
            this.operationId = operationId;
            this.success = success;
            this.duration = duration;
            this.speedMBps = speedMBps;
            this.s3Key = s3Key;
            this.completedAt = completedAt;
        }
    }
}
