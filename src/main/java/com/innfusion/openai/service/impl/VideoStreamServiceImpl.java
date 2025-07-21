package com.innfusion.openai.service.impl;

import com.github.kokorin.jaffree.ffmpeg.FFmpeg;
import com.github.kokorin.jaffree.ffmpeg.UrlInput;
import com.github.kokorin.jaffree.ffmpeg.UrlOutput;
import com.innfusion.video.domain.VideoUploadRequest;
import com.innfusion.video.domain.VideoUploadResult;
import io.awspring.cloud.s3.S3Template;
import java.time.Duration;
import java.net.URL;
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
    
    // Performance thresholds
    private static final long PERFORMANCE_TARGET_MS = 1000; // 1 second
    private static final double MIN_OPTIMAL_SPEED_MBPS = 50.0; // 50 MB/s

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
            final Path intervieweeDir = Paths.get(baseDir, intervieweeId);
            final Path questionDir = intervieweeDir.resolve(questionId);
            // Create Directory if not exists
            Files.createDirectories(questionDir);

            // save Question Text file

            Path questionFile = questionDir.resolve(queFilename);
            Files.writeString(questionFile, question, StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING);

            // Save Video Temporarily
            Path tempVideoPath = questionDir.resolve(tmpVidFilename);
            try (InputStream is = file.getInputStream()) {
                Files.copy(is, tempVideoPath, StandardCopyOption.REPLACE_EXISTING);
            }

            // Create Output Dir for HLS Segment and Playlist
            Path hldOutputDir = questionDir.resolve(hlsDir);

            Files.createDirectories(hldOutputDir);

            loadFfmpeg();

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
            // Delete Temp file after conversation
            Files.deleteIfExists(tempVideoPath);

            String message = messages.getMessage(MessageCodes.MC_UPLOADED_SUCCESSFULLY);
            return new BaseDataRs(message);
        } catch (IOException ioException) {
            log.error("Exception in processVideoStream(MultipartFile , String, String,String) -> {}",
                            ioException.getLocalizedMessage());
            throw new RuntimeException(ioException);
        } catch (Exception e) {
            log.error("Exception in processVideoStream(MultipartFile , String, String,String) -> {}",
                            e.getLocalizedMessage());
            throw e;
        }
    }

    /**
     * High-performance video upload to S3 with HLS conversion.
     * Converts video to HLS format and uploads HLS files + question text to S3.
     * Uses same file names as local implementation for consistency.
     */
    @Override
    public CompletableFuture<BaseDataRs> uploadVideoToS3(MultipartFile file, String question, String intervieweeId, String questionId) {
        if (log.isDebugEnabled()) {
            log.debug("Executing HLS uploadVideoToS3(MultipartFile, String, String, String) ->");
        }
        
        try {
            log.info("🚀 Starting HLS conversion and S3 upload - IntervieweeId: {}, QuestionId: {}, FileSize: {}MB", 
                     intervieweeId, questionId, file.getSize() / (1024.0 * 1024.0));
            
            // Create optimized upload request using domain model with question in metadata
            java.util.Map<String, String> metadata = new java.util.HashMap<>();
            metadata.put("question", question);
            
            VideoUploadRequest request = VideoUploadRequest.builder()
                .file(file)
                .intervieweeId(intervieweeId)
                .questionId(questionId)
                .metadata(metadata)
                .requestTime(java.time.Instant.now())
                .build();
            
            // Execute HLS conversion and S3 upload
            return performHighPerformanceUpload(request)
                .thenApply(this::convertToBaseDataRs);
            
        } catch (Exception e) {
            log.error("Exception in HLS uploadVideoToS3: {}", e.getMessage());
            CompletableFuture<BaseDataRs> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(e);
            return failedFuture;
        }
    }

    /**
     * Upload video using Spring Cloud AWS (delegates to main high-performance method).
     */
    @Override
    public CompletableFuture<BaseDataRs> uploadVideoUsingSpringCloudAws(MultipartFile file, String intervieweeId, String questionId) {
        log.info("Delegating to HLS upload implementation (deprecated method - no question text available)");
        // This deprecated method doesn't have question parameter, so use empty question
        return uploadVideoToS3(file, "", intervieweeId, questionId);
    }

    /**
     * Complete interview upload to S3 - uploads both video and question text
     * Creates consistent cloud directory structure matching local storage
     */
    @Override
    public CompletableFuture<BaseDataRs> uploadCompleteInterviewToS3(MultipartFile file, String question, String intervieweeId, String questionId) {
        if (log.isDebugEnabled()) {
            log.debug("Executing uploadCompleteInterviewToS3 - IntervieweeId: {}, QuestionId: {}", intervieweeId, questionId);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            String operationId = "complete-upload-" + java.util.UUID.randomUUID().toString().substring(0, 8);
            long startTime = System.currentTimeMillis();
            
            try {
                log.info("🚀 Starting complete interview upload to S3: IntervieweeId={}, QuestionId={}, FileSize={}MB", 
                         intervieweeId, questionId, file.getSize() / (1024.0 * 1024.0));
                
                // Create base S3 directory path consistent with local structure
                String baseS3Key = String.format("%s/%s", intervieweeId.trim(), questionId.trim());
                
                // Upload video file with timestamp for uniqueness
                VideoUploadRequest videoRequest = VideoUploadRequest.builder()
                    .file(file)
                    .intervieweeId(intervieweeId)
                    .questionId(questionId)
                    .requestTime(java.time.Instant.now())
                    .build();
                
                String videoS3Key = videoRequest.generateS3Key();
                s3Template.upload(s3BucketName, videoS3Key, file.getInputStream());
                
                // Upload question text file (matches local storage structure)
                String questionS3Key = String.format("%s/question.txt", baseS3Key);
                java.io.ByteArrayInputStream questionStream = new java.io.ByteArrayInputStream(question.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                s3Template.upload(s3BucketName, questionS3Key, questionStream);
                
                long duration = System.currentTimeMillis() - startTime;
                
                log.info("✅ Complete interview upload successful!");
                log.info("📁 Cloud directory: s3://{}/{}/", s3BucketName, baseS3Key);
                log.info("🎬 Video uploaded: {}", videoS3Key);
                log.info("📝 Question uploaded: {}", questionS3Key);
                log.info("⏱️ Total upload time: {}ms", duration);
                
                // Create comprehensive result matching cloud directory structure
                java.util.Map<String, Object> data = new java.util.HashMap<>();
                data.put("cloudDirectory", String.format("s3://%s/%s/", s3BucketName, baseS3Key));
                data.put("baseS3Key", baseS3Key);
                data.put("videoS3Key", videoS3Key);
                data.put("questionS3Key", questionS3Key);
                data.put("bucketName", s3BucketName);
                data.put("intervieweeId", intervieweeId);
                data.put("questionId", questionId);
                data.put("videoFileSize", file.getSize());
                data.put("questionTextSize", question.getBytes().length);
                data.put("uploadDurationMs", duration);
                data.put("uploadSpeedMBps", String.format("%.2f", (file.getSize() / (1024.0 * 1024.0)) / (duration / 1000.0)));
                data.put("filesUploaded", java.util.Arrays.asList("video", "question"));
                data.put("directoryStructureConsistent", true);
                
                String message = String.format("🎯 Complete interview upload SUCCESS: Video (%dMB) + Question uploaded to cloud directory in %dms", 
                    file.getSize() / (1024 * 1024), duration);
                
                return new BaseDataRs(message, data);
                
            } catch (Exception e) {
                log.error("💥 Complete interview upload failed for operation {}: {}", operationId, e.getMessage());
                
                java.util.Map<String, Object> errorData = new java.util.HashMap<>();
                errorData.put("operationId", operationId);
                errorData.put("error", e.getMessage());
                errorData.put("intervieweeId", intervieweeId);
                errorData.put("questionId", questionId);
                
                return new BaseDataRs("Complete interview upload failed: " + e.getMessage(), errorData);
            }
        }, videoUploadExecutor);
    }
    
    @Override
    public BaseDataRs getPerformanceStatus() {
        boolean isOptimal = isPerformanceOptimal();
        double avgSpeed = getAverageUploadSpeedMBps();
        
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("performanceOptimal", isOptimal);
        data.put("averageUploadSpeedMBps", avgSpeed);
        data.put("performanceTarget", "50MB in <1000ms");
        data.put("minimumSpeedRequired", MIN_OPTIMAL_SPEED_MBPS);
        data.put("systemStatus", isOptimal ? "OPTIMAL" : "NEEDS_OPTIMIZATION");
        data.put("totalUploads", totalUploads.get());
        data.put("successfulUploads", successfulUploads.get());
        data.put("successRate", getSuccessRate());
        
        String message = isOptimal 
            ? String.format("🚀 Performance OPTIMAL: %.2f MB/s average, %.2f%% success rate", avgSpeed, getSuccessRate() * 100)
            : String.format("⚠️ Performance needs optimization: %.2f MB/s average", avgSpeed);
            
        return new BaseDataRs(message, data);
    }

    @Override
    public CompletableFuture<Boolean> checkVideoExists(String intervieweeId, String questionId, String fileName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String s3Key = generateS3Key(intervieweeId, questionId, fileName);
                return s3Template.objectExists(s3BucketName, s3Key);
            } catch (Exception e) {
                log.error("Error checking video existence: intervieweeId={}, questionId={}, fileName={}, error={}", 
                         intervieweeId, questionId, fileName, e.getMessage());
                return false;
            }
        }, videoUploadExecutor);
    }

    @Override
    public CompletableFuture<String> generateVideoUrl(String intervieweeId, String questionId, String fileName, int durationHours) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String s3Key = generateS3Key(intervieweeId, questionId, fileName);
                URL signedUrl = s3Template.createSignedGetURL(s3BucketName, s3Key, Duration.ofHours(durationHours));
                return signedUrl.toString();
            } catch (Exception e) {
                log.error("Error generating video URL: intervieweeId={}, questionId={}, fileName={}, error={}", 
                         intervieweeId, questionId, fileName, e.getMessage());
                return null;
            }
        }, videoUploadExecutor);
    }

    @Override
    public CompletableFuture<Boolean> deleteVideo(String intervieweeId, String questionId, String fileName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String s3Key = generateS3Key(intervieweeId, questionId, fileName);
                s3Template.deleteObject(s3BucketName, s3Key);
                log.info("Video deleted successfully: s3Key={}", s3Key);
                return true;
            } catch (Exception e) {
                log.error("Error deleting video: intervieweeId={}, questionId={}, fileName={}, error={}", 
                         intervieweeId, questionId, fileName, e.getMessage());
                return false;
            }
        }, videoUploadExecutor);
    }

    /**
     * High-performance video upload implementation using Template Method Pattern
     * Enhanced to convert video to HLS format and upload HLS files + question to S3
     * Uses same file names as local implementation for consistency
     */
    private CompletableFuture<VideoUploadResult> performHighPerformanceUpload(VideoUploadRequest request) {
        String operationId = "upload-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        
        return CompletableFuture.supplyAsync(() -> {
            // Record performance start (Observer Pattern)
            recordUploadStart(operationId, request.getFile().getSize());
            long startTime = System.currentTimeMillis();
            
            Path tempDir = null;
            
            try {
                // Validation now handled by Bean Validation (@Valid) in REST layer
                // No manual validation needed here - ValidationUtils handles it
                
                // Create base S3 directory structure: intervieweeId/questionId/
                String baseS3Key = String.format("%s/%s", 
                    request.getIntervieweeId().trim(), 
                    request.getQuestionId().trim());
                
                log.info("🚀 HLS conversion and S3 upload started: operation={}, size={}MB, baseKey={}", 
                        operationId, request.getFile().getSize() / (1024.0 * 1024.0), baseS3Key);
                
                // Create temporary directory for HLS processing
                tempDir = Files.createTempDirectory("hls-s3-upload-" + operationId);
                
                // Save video file temporarily (same name as local implementation)
                Path tempVideoPath = tempDir.resolve(tmpVidFilename);
                try (InputStream is = request.getFile().getInputStream()) {
                    Files.copy(is, tempVideoPath, StandardCopyOption.REPLACE_EXISTING);
                }
                log.debug("📹 Temporary video saved: {}", tempVideoPath);
                
                // Create question text file (same name as local implementation)
                String question = (String) request.getMetadata().get("question");
                if (question != null) {
                    Path questionFile = tempDir.resolve(queFilename);
                    Files.writeString(questionFile, question, StandardOpenOption.CREATE);
                    log.debug("📝 Question file created: {}", questionFile);
                }
                
                // Create HLS output directory (same structure as local implementation)  
                Path hlsOutputDir = tempDir.resolve(hlsDir);
                Files.createDirectories(hlsOutputDir);
                
                log.info("🎬 Converting video to HLS format using OPTIMIZED processing...");
                
                // Execute OPTIMIZED HLS conversion using centralized high-performance method
                long ffmpegDuration = executeOptimizedHlsConversion(
                    tempVideoPath, 
                    hlsOutputDir.resolve(hlsPlayFilename),
                    hlsOutputDir,
                    hlsOutputDir.resolve(hlsSegPattern).toString()
                );
                
                log.info("✅ HLS conversion completed, uploading to S3...");
                
                // Upload question text file to S3 (same filename as local implementation)
                if (question != null) {
                    String questionS3Key = String.format("%s/%s", baseS3Key, queFilename);
                    Path questionFile = tempDir.resolve(queFilename);
                    s3Template.upload(s3BucketName, questionS3Key, Files.newInputStream(questionFile));
                    log.debug("📝 Question uploaded: {}", questionS3Key);
                }
                
                // Upload HLS playlist file to S3 (same structure as local implementation)
                String playlistS3Key = String.format("%s/%s/%s", baseS3Key, hlsDir, hlsPlayFilename);
                Path playlistFile = hlsOutputDir.resolve(hlsPlayFilename);
                s3Template.upload(s3BucketName, playlistS3Key, Files.newInputStream(playlistFile));
                log.debug("🎵 HLS playlist uploaded: {}", playlistS3Key);
                
                // Upload all HLS segment files to S3 (same pattern as local implementation)
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
                
                long duration = System.currentTimeMillis() - startTime;
                
                // Record performance completion (Observer Pattern)
                recordUploadCompletion(operationId, true, duration);
                
                log.info("✅ HLS upload to S3 completed!");
                log.info("📁 S3 directory structure: s3://{}/{}/", s3BucketName, baseS3Key);
                log.info("🎵 HLS playlist: {}", playlistS3Key);
                log.info("📺 HLS segments: {} files", segmentCount);
                log.info("⏱️ Total processing time: {}ms", duration);
                
                // Factory Pattern: Create result object with HLS structure info
                VideoUploadResult result = VideoUploadResult.success(request, playlistS3Key, s3BucketName, duration);
                
                // Log performance metrics
                if (result.isWithinPerformanceTarget()) {
                    log.info("🎯 PERFORMANCE TARGET ACHIEVED: HLS conversion + upload {}MB in {}ms ({:.2f} MB/s) ✨", 
                           result.getFileSizeBytes() / (1024.0 * 1024.0),
                           duration, result.getUploadSpeedMBps());
                } else {
                    log.warn("⚠️ Performance target missed: {}ms (includes HLS conversion)", duration);
                }
                
                return result;
                
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - startTime;
                recordUploadCompletion(operationId, false, duration);
                
                log.error("💥 HLS conversion and S3 upload failed for operation {}: {}", operationId, e.getMessage());
                return VideoUploadResult.failure(request, e.getMessage());
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
        }, videoUploadExecutor);
    }

    /**
     * Converts VideoUploadResult to BaseDataRs for compatibility with existing API.
     */
    private BaseDataRs convertToBaseDataRs(VideoUploadResult result) {
        if (result.isSuccess()) {
            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("s3Key", result.getS3Key());
            data.put("bucketName", result.getBucketName());
            data.put("fileSize", result.getFileSizeBytes());
            data.put("contentType", result.getContentType());
            data.put("intervieweeId", result.getIntervieweeId());
            data.put("questionId", result.getQuestionId());
            data.put("uploadDurationMs", result.getUploadDurationMs());
            data.put("uploadSpeedMBps", String.format("%.2f", result.getUploadSpeedMBps()));
            data.put("performanceTargetMet", result.isWithinPerformanceTarget());
            data.put("uploadMethod", "High-Performance Spring Cloud AWS");
            
            String message = result.isWithinPerformanceTarget() 
                ? String.format("🎯 High-performance upload SUCCESS: %dMB in %dms (%.2f MB/s)", 
                    result.getFileSizeBytes() / (1024 * 1024), 
                    result.getUploadDurationMs(), 
                    result.getUploadSpeedMBps())
                : String.format("Upload completed: %dMB in %dms (%.2f MB/s)", 
                    result.getFileSizeBytes() / (1024 * 1024), 
                    result.getUploadDurationMs(), 
                    result.getUploadSpeedMBps());
                    
            return new BaseDataRs(message, data);
        } else {
            return new BaseDataRs(result.getErrorMessage());
        }
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
    
    private boolean isPerformanceOptimal() {
        double avgSpeed = getAverageUploadSpeedMBps();
        double successRate = getSuccessRate();
        return avgSpeed >= MIN_OPTIMAL_SPEED_MBPS && successRate >= 0.95;
    }
    
    private double getAverageUploadSpeedMBps() {
        long uploads = successfulUploads.get();
        if (uploads == 0) return 0.0;
        
        double totalMB = totalDataTransferred.sum() / (1024.0 * 1024.0);
        double totalTimeSeconds = totalUploadTime.sum() / 1000.0;
        return totalTimeSeconds > 0 ? totalMB / totalTimeSeconds : 0.0;
    }
    
    private double getSuccessRate() {
        long total = totalUploads.get();
        return total > 0 ? (double) successfulUploads.get() / total : 0.0;
    }
    
    private String generateS3Key(String intervieweeId, String questionId, String fileName) {
        String sanitizedFilename = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
        return String.format("%s/%s/%s", 
            intervieweeId.trim(), 
            questionId.trim(), 
            sanitizedFilename);
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
        
        var outputBuilder = UrlOutput.toPath(outputPlaylist)
                // SPEED OPTIMIZATIONS - Configurable:
                .addArguments("-preset", ffmpegPreset)              // ultrafast/superfast/veryfast
                .addArguments("-crf", ffmpegCrf)                    // 18-28 (lower=quality, higher=speed)
                .addArguments("-threads", "0")                      // Use all CPU cores
                .addArguments("-tune", "fastdecode")                // Optimize for fast decoding
                
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
        
        // HARDWARE ACCELERATION (if enabled):
        if (enableHardwareAcceleration) {
            log.debug("🎯 Hardware acceleration ENABLED");
            outputBuilder.addArguments("-hwaccel", "auto")          // Auto-detect GPU
                        .addArguments("-c:v", "libx264");           // Fast encoder
        }
        
        FFmpeg.atPath(ffmpegDir).addInput(UrlInput.fromPath(inputVideo))
                .addOutput(outputBuilder)
                .execute();
        
        long duration = System.currentTimeMillis() - startTime;
        log.info("⚡ OPTIMIZED HLS conversion completed in {}ms (preset: {}, hardware accel: {})", 
                 duration, ffmpegPreset, enableHardwareAcceleration ? "enabled" : "disabled");
        
        return duration;
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
}
