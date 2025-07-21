package com.innfusion.video.validation;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for VideoFileValidator.
 * Tests all validation scenarios including edge cases and security checks.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("VideoFileValidator Tests")
class VideoFileValidatorTest {

    @Mock
    private ConstraintValidatorContext context;
    
    @Mock
    private ConstraintValidatorContext.ConstraintViolationBuilder violationBuilder;
    
    private VideoFileValidator validator;
    
    @BeforeEach
    void setUp() {
        validator = new VideoFileValidator();
        
        // Mock the constraint annotation with default values
        VideoFile annotation = createMockAnnotation();
        validator.initialize(annotation);
        
        // Setup mock behavior for constraint violations
        when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(violationBuilder);
        when(violationBuilder.addConstraintViolation()).thenReturn(context);
        doNothing().when(context).disableDefaultConstraintViolation();
    }
    
    @Test
    @DisplayName("Valid MP4 video file should pass validation")
    void testValidMP4File() {
        // Given
        MultipartFile validFile = new MockMultipartFile(
            "video",
            "test-video.mp4",
            "video/mp4",
            createVideoContent(1024 * 1024) // 1MB
        );
        
        // When
        boolean result = validator.isValid(validFile, context);
        
        // Then
        assertTrue(result, "Valid MP4 file should pass validation");
        verify(context, never()).disableDefaultConstraintViolation();
    }
    
    @Test
    @DisplayName("Null file should fail validation")
    void testNullFile() {
        // When
        boolean result = validator.isValid(null, context);
        
        // Then
        assertFalse(result, "Null file should fail validation");
        verify(context).buildConstraintViolationWithTemplate("Video file cannot be null or empty");
    }
    
    @Test
    @DisplayName("Empty file should fail validation")
    void testEmptyFile() {
        // Given
        MultipartFile emptyFile = new MockMultipartFile(
            "video",
            "empty.mp4",
            "video/mp4",
            new byte[0]
        );
        
        // When
        boolean result = validator.isValid(emptyFile, context);
        
        // Then
        assertFalse(result, "Empty file should fail validation");
        verify(context).buildConstraintViolationWithTemplate("Video file cannot be null or empty");
    }
    
    @Test
    @DisplayName("File exceeding size limit should fail validation")
    void testFileTooLarge() {
        // Given
        MultipartFile largeFile = new MockMultipartFile(
            "video",
            "large-video.mp4",
            "video/mp4",
            createVideoContent(60 * 1024 * 1024) // 60MB (exceeds 50MB limit)
        );
        
        // When
        boolean result = validator.isValid(largeFile, context);
        
        // Then
        assertFalse(result, "Large file should fail validation");
        verify(context).buildConstraintViolationWithTemplate(
            argThat(message -> message.contains("File size") && message.contains("exceeds maximum allowed size"))
        );
    }
    
    @Test
    @DisplayName("File below minimum size should fail validation")
    void testFileTooSmall() {
        // Given
        MultipartFile smallFile = new MockMultipartFile(
            "video",
            "tiny-video.mp4",
            "video/mp4",
            createVideoContent(512) // 512 bytes (below 1KB minimum)
        );
        
        // When
        boolean result = validator.isValid(smallFile, context);
        
        // Then
        assertFalse(result, "Small file should fail validation");
        verify(context).buildConstraintViolationWithTemplate(
            argThat(message -> message.contains("File size") && message.contains("is below minimum required size"))
        );
    }
    
    @Test
    @DisplayName("Invalid content type should fail validation")
    void testInvalidContentType() {
        // Given
        MultipartFile invalidFile = new MockMultipartFile(
            "document",
            "document.pdf",
            "application/pdf",
            createVideoContent(1024 * 1024) // 1MB
        );
        
        // When
        boolean result = validator.isValid(invalidFile, context);
        
        // Then
        assertFalse(result, "Non-video content type should fail validation");
        verify(context).buildConstraintViolationWithTemplate(
            argThat(message -> message.contains("Content type 'application/pdf' is not allowed"))
        );
    }
    
    @Test
    @DisplayName("Invalid file extension should fail validation")
    void testInvalidFileExtension() {
        // Given
        MultipartFile invalidFile = new MockMultipartFile(
            "video",
            "fake-video.txt",
            "video/mp4",
            createVideoContent(1024 * 1024) // 1MB
        );
        
        // When
        boolean result = validator.isValid(invalidFile, context);
        
        // Then
        assertFalse(result, "Invalid file extension should fail validation");
        verify(context).buildConstraintViolationWithTemplate(
            argThat(message -> message.contains("File extension is not allowed") && message.contains("fake-video.txt"))
        );
    }
    
    @Test
    @DisplayName("File with path traversal should fail security validation")
    void testPathTraversalAttack() {
        // Given
        MultipartFile maliciousFile = new MockMultipartFile(
            "video",
            "../../../etc/passwd.mp4",
            "video/mp4",
            createVideoContent(1024 * 1024) // 1MB
        );
        
        // When
        boolean result = validator.isValid(maliciousFile, context);
        
        // Then
        assertFalse(result, "Path traversal attempt should fail validation");
        verify(context).buildConstraintViolationWithTemplate("File name contains invalid path characters");
    }
    
    @Test
    @DisplayName("File with dangerous extension should fail security validation")
    void testDangerousFileExtension() {
        // Given
        MultipartFile maliciousFile = new MockMultipartFile(
            "video",
            "virus.exe.mp4",
            "video/mp4",
            createVideoContent(1024 * 1024) // 1MB
        );
        
        // When
        boolean result = validator.isValid(maliciousFile, context);
        
        // Then
        assertFalse(result, "Dangerous file extension should fail validation");
        verify(context).buildConstraintViolationWithTemplate(
            argThat(message -> message.contains("File name contains potentially dangerous content") && message.contains(".exe"))
        );
    }
    
    @Test
    @DisplayName("Various valid video formats should pass validation")
    void testMultipleValidFormats() {
        // Test data for various valid formats
        String[][] validFormats = {
            {"test.mp4", "video/mp4"},
            {"test.avi", "video/avi"},
            {"test.mov", "video/mov"},
            {"test.quicktime", "video/quicktime"},
            {"test.webm", "video/webm"},
            {"test.ogg", "video/ogg"},
            {"test.3gp", "video/3gpp"}
        };
        
        for (String[] format : validFormats) {
            // Given
            MultipartFile validFile = new MockMultipartFile(
                "video",
                format[0],
                format[1],
                createVideoContent(1024 * 1024) // 1MB
            );
            
            // When
            boolean result = validator.isValid(validFile, context);
            
            // Then
            assertTrue(result, "Valid format " + format[0] + " should pass validation");
        }
    }
    
    @Test
    @DisplayName("File at exact size limits should pass validation")
    void testExactSizeLimits() {
        // Test minimum size (1KB exactly)
        MultipartFile minSizeFile = new MockMultipartFile(
            "video",
            "min-size.mp4",
            "video/mp4",
            createVideoContent(1024) // Exactly 1KB
        );
        
        assertTrue(validator.isValid(minSizeFile, context), 
                  "File at minimum size should pass validation");
        
        // Test maximum size (50MB exactly)
        MultipartFile maxSizeFile = new MockMultipartFile(
            "video",
            "max-size.mp4",
            "video/mp4",
            createVideoContent(52428800) // Exactly 50MB
        );
        
        assertTrue(validator.isValid(maxSizeFile, context), 
                  "File at maximum size should pass validation");
    }
    
    private VideoFile createMockAnnotation() {
        return new VideoFile() {
            @Override
            public String message() { return "Invalid video file"; }
            
            @Override
            public Class<?>[] groups() { return new Class[0]; }
            
            @Override
            public Class[] payload() { return new Class[0]; }
            
            @Override
            public long maxSizeBytes() { return 52_428_800L; } // 50MB
            
            @Override
            public long minSizeBytes() { return 1024L; } // 1KB
            
            @Override
            public String[] allowedContentTypes() {
                return new String[]{
                    "video/mp4", "video/avi", "video/mov", "video/quicktime",
                    "video/x-msvideo", "video/webm", "video/ogg", "video/3gpp"
                };
            }
            
            @Override
            public String[] allowedExtensions() {
                return new String[]{
                    ".mp4", ".avi", ".mov", ".webm", ".ogg", ".3gp", ".mkv"
                };
            }
            
            @Override
            public Class annotationType() { return VideoFile.class; }
        };
    }
    
    private byte[] createVideoContent(int size) {
        byte[] content = new byte[size];
        // Fill with some dummy video-like content
        for (int i = 0; i < size; i++) {
            content[i] = (byte) (i % 256);
        }
        return content;
    }
} 