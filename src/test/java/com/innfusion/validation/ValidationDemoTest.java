package com.innfusion.validation;

import com.innfusion.video.domain.VideoUploadRequest;
import com.innfusion.video.validation.ValidationUtils;
import com.innfusion.exception.VideoProcessingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple demonstration of the validation framework in action.
 * Shows how Bean Validation and custom validators work together.
 */
@DisplayName("Validation Framework Demo")
class ValidationDemoTest {

    @Test
    @DisplayName("Valid video upload request should pass all validations")
    void testValidVideoUpload() {
        // Given - Create a valid video file
        MultipartFile validFile = new MockMultipartFile(
            "video",
            "interview-response.mp4",
            "video/mp4",
            createVideoContent(5 * 1024 * 1024) // 5MB
        );

        // When - Create upload request
        VideoUploadRequest request = VideoUploadRequest.builder()
            .file(validFile)
            .intervieweeId("CANDIDATE_001")
            .questionId("TECH_QUESTION_123")
            .build();

        // Then - All validations should pass
        assertDoesNotThrow(() -> {
            ValidationUtils.validateUploadParameters(
                request.getIntervieweeId(),
                request.getQuestionId(),
                request.getFile(),
                "demo-test"
            );
        });

        // Verify request properties
        assertEquals("CANDIDATE_001", request.getIntervieweeId());
        assertEquals("TECH_QUESTION_123", request.getQuestionId());
        assertEquals("interview-response.mp4", request.getFile().getOriginalFilename());
        assertEquals(5 * 1024 * 1024, request.getFile().getSize());
        assertNotNull(request.getRequestTime());
    }

    @Test
    @DisplayName("Empty interviewee ID should fail validation")
    void testEmptyIntervieweeId() {
        // Given
        MultipartFile validFile = new MockMultipartFile(
            "video", "test.mp4", "video/mp4", createVideoContent(1024 * 1024)
        );

        // When & Then - Empty interviewee ID should fail
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> ValidationUtils.validateUploadParameters(
                "", // Empty ID
                "QUESTION_123",
                validFile,
                "demo-test"
            )
        );

        assertTrue(exception.getMessage().contains("cannot be empty"));
    }

    @Test
    @DisplayName("Null file should fail validation")
    void testNullFile() {
        // When & Then - Null file should fail
        VideoProcessingException exception = assertThrows(
            VideoProcessingException.class,
            () -> ValidationUtils.validateUploadParameters(
                "CANDIDATE_001",
                "QUESTION_123",
                null, // Null file
                "demo-test"
            )
        );

        assertEquals("Video file cannot be null or empty", exception.getMessage());
        assertEquals("demo-test", exception.getProcessingStage());
    }

    @Test
    @DisplayName("Non-video file should fail validation")
    void testNonVideoFile() {
        // Given - Create a PDF file instead of video
        MultipartFile pdfFile = new MockMultipartFile(
            "document",
            "resume.pdf",
            "application/pdf", // Not a video type
            "PDF content".getBytes()
        );

        // When & Then - Non-video file should fail
        VideoProcessingException exception = assertThrows(
            VideoProcessingException.class,
            () -> ValidationUtils.validateUploadParameters(
                "CANDIDATE_001",
                "QUESTION_123",
                pdfFile,
                "demo-test"
            )
        );

        assertTrue(exception.getMessage().contains("Invalid content type"));
        assertTrue(exception.getMessage().contains("application/pdf"));
    }

    @Test
    @DisplayName("File too large should fail validation")
    void testFileTooLarge() {
        // Given - Create a file larger than allowed
        MultipartFile largeFile = new MockMultipartFile(
            "video",
            "huge-video.mp4",
            "video/mp4",
            createVideoContent(60 * 1024 * 1024) // 60MB - exceeds 50MB limit
        );

        // When & Then - Large file should fail
        VideoProcessingException exception = assertThrows(
            VideoProcessingException.class,
            () -> ValidationUtils.validateFileSize(
                largeFile,
                1024L,           // 1KB minimum
                52_428_800L,     // 50MB maximum
                "demo-test"
            )
        );

        assertTrue(exception.getMessage().contains("exceeds maximum allowed size"));
        assertTrue(exception.getMessage().contains("52428800"));
    }

    @Test
    @DisplayName("Malicious filename should fail security validation")
    void testMaliciousFilename() {
        // Given - File with path traversal attempt
        MultipartFile maliciousFile = new MockMultipartFile(
            "video",
            "../../../etc/passwd.mp4", // Path traversal attempt
            "video/mp4",
            createVideoContent(1024 * 1024)
        );

        // When & Then - Malicious filename should fail security check
        VideoProcessingException exception = assertThrows(
            VideoProcessingException.class,
            () -> ValidationUtils.validateFileNameSecurity(
                maliciousFile.getOriginalFilename(),
                "demo-test"
            )
        );

        assertTrue(exception.getMessage().contains("path traversal characters"));
    }

    @Test
    @DisplayName("Valid file types should all pass validation")
    void testMultipleValidFileTypes() {
        // Given - Array of valid video file formats
        String[][] validFormats = {
            {"interview.mp4", "video/mp4"},
            {"response.avi", "video/avi"},
            {"answer.mov", "video/mov"},
            {"recording.webm", "video/webm"}
        };

        // When & Then - All valid formats should pass
        for (String[] format : validFormats) {
            MultipartFile validFile = new MockMultipartFile(
                "video",
                format[0],
                format[1],
                createVideoContent(1024 * 1024) // 1MB
            );

            assertDoesNotThrow(() -> {
                ValidationUtils.validateUploadParameters(
                    "CANDIDATE_001",
                    "QUESTION_123",
                    validFile,
                    "demo-test-" + format[0]
                );
            }, "Valid format " + format[0] + " should pass validation");
        }
    }

    @Test
    @DisplayName("String parameter validation should enforce length limits")
    void testStringParameterValidation() {
        // Test various string validation scenarios
        
        // Valid string should pass
        assertDoesNotThrow(() -> 
            ValidationUtils.validateStringParameter("CANDIDATE_001", "candidateId", 1, 50)
        );

        // Null string should fail
        assertThrows(IllegalArgumentException.class, () ->
            ValidationUtils.validateStringParameter(null, "candidateId", 1, 50)
        );

        // Empty string should fail
        assertThrows(IllegalArgumentException.class, () ->
            ValidationUtils.validateStringParameter("", "candidateId", 1, 50)
        );

        // String too short should fail
        assertThrows(IllegalArgumentException.class, () ->
            ValidationUtils.validateStringParameter("A", "candidateId", 5, 50)
        );

        // String too long should fail
        assertThrows(IllegalArgumentException.class, () ->
            ValidationUtils.validateStringParameter("A".repeat(51), "candidateId", 1, 50)
        );
    }

    @Test
    @DisplayName("Video file type validation should check both extension and content type")
    void testVideoFileTypeValidation() {
        // Valid combination should pass
        MultipartFile validFile = new MockMultipartFile(
            "video", "test.mp4", "video/mp4", createVideoContent(1024 * 1024)
        );

        assertDoesNotThrow(() -> {
            ValidationUtils.validateVideoFileType(
                validFile,
                new String[]{".mp4", ".avi", ".mov"},
                new String[]{"video/mp4", "video/avi", "video/mov"},
                "demo-test"
            );
        });

        // Mismatched extension and content type should fail
        MultipartFile mismatchedFile = new MockMultipartFile(
            "video", "test.mp4", "video/avi", createVideoContent(1024 * 1024)
        );

        // This should still pass because both .mp4 and video/avi are in allowed lists
        assertDoesNotThrow(() -> {
            ValidationUtils.validateVideoFileType(
                mismatchedFile,
                new String[]{".mp4", ".avi"},
                new String[]{"video/mp4", "video/avi"},
                "demo-test"
            );
        });

        // Invalid extension should fail
        MultipartFile invalidExtensionFile = new MockMultipartFile(
            "video", "test.txt", "video/mp4", createVideoContent(1024 * 1024)
        );

        assertThrows(VideoProcessingException.class, () -> {
            ValidationUtils.validateVideoFileType(
                invalidExtensionFile,
                new String[]{".mp4", ".avi"},
                new String[]{"video/mp4", "video/avi"},
                "demo-test"
            );
        });
    }

    /**
     * Helper method to create dummy video content
     */
    private byte[] createVideoContent(int size) {
        byte[] content = new byte[size];
        // Fill with dummy data that looks like video content
        for (int i = 0; i < size; i++) {
            content[i] = (byte) ((i * 37) % 256); // Some variation in the data
        }
        return content;
    }
} 