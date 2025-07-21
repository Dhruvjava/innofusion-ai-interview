# 🎯 **Validation Framework - Practical Examples**

## 📘 **Complete Usage Guide**

This guide shows **real-world examples** of how to use our validation framework for video upload scenarios.

## 🎪 **1. Basic Video Upload Example**

### **Valid Upload Request**
```bash
curl -X POST "http://localhost:8080/api/v1/video/upload" \
     -F "file=@interview-response.mp4" \
     -F "intervieweeId=CANDIDATE_2024_001" \
     -F "questionId=BEHAVIORAL_Q1"
```

### **✅ Success Response**
```json
{
  "message": "🎯 High-performance upload SUCCESS: 15MB in 780ms (19.2 MB/s)",
  "data": {
    "s3Key": "CANDIDATE_2024_001/BEHAVIORAL_Q1/1640995200000_interview-response.mp4",
    "bucketName": "innfusion-video-uploads",
    "fileSize": 15728640,
    "contentType": "video/mp4",
    "intervieweeId": "CANDIDATE_2024_001",
    "questionId": "BEHAVIORAL_Q1",
    "uploadDurationMs": 780,
    "uploadSpeedMBps": "19.2",
    "performanceTargetMet": true,
    "uploadMethod": "High-Performance Spring Cloud AWS"
  }
}
```

## ❌ **2. Validation Error Examples**

### **Missing File Error**
```bash
curl -X POST "http://localhost:8080/api/v1/video/upload" \
     -F "intervieweeId=CANDIDATE_001" \
     -F "questionId=TECH_Q1"
```

```json
{
  "message": "Required request part 'file' is not present",
  "data": {
    "errorType": "VALIDATION_ERROR",
    "field": "file",
    "suggestion": "Please include a video file in your request"
  }
}
```

### **File Too Large Error**
```bash
curl -X POST "http://localhost:8080/api/v1/video/upload" \
     -F "file=@huge-presentation.mp4" \
     -F "intervieweeId=CANDIDATE_001" \
     -F "questionId=TECH_Q1"
```

```json
{
  "message": "File size exceeds maximum allowed size (52428800 bytes)",
  "data": {
    "errorType": "FILE_SIZE_EXCEEDED",
    "maxSize": 52428800,
    "actualSize": 157286400,
    "suggestion": "Please reduce file size or compress the video. Maximum allowed: 50MB"
  }
}
```

### **Invalid File Type Error**
```bash
curl -X POST "http://localhost:8080/api/v1/video/upload" \
     -F "file=@resume.pdf" \
     -F "intervieweeId=CANDIDATE_001" \
     -F "questionId=TECH_Q1"
```

```json
{
  "message": "Constraint validation failed: Content type 'application/pdf' is not allowed. Allowed types: video/mp4, video/avi, video/mov, video/quicktime, video/x-msvideo, video/webm, video/ogg, video/3gpp",
  "data": {
    "constraintViolations": [
      "Content type 'application/pdf' is not allowed. Allowed types: video/mp4, video/avi, video/mov, video/quicktime, video/x-msvideo, video/webm, video/ogg, video/3gpp"
    ],
    "errorType": "CONSTRAINT_VIOLATION",
    "totalViolations": 1
  }
}
```

### **Empty Parameter Error**
```bash
curl -X POST "http://localhost:8080/api/v1/video/upload" \
     -F "file=@video.mp4" \
     -F "intervieweeId=" \
     -F "questionId=TECH_Q1"
```

```json
{
  "message": "Constraint validation failed: intervieweeId cannot be empty",
  "data": {
    "constraintViolations": [
      "Interviewee ID is required and cannot be empty"
    ],
    "errorType": "CONSTRAINT_VIOLATION"
  }
}
```

### **Parameter Too Long Error**
```bash
curl -X POST "http://localhost:8080/api/v1/video/upload" \
     -F "file=@video.mp4" \
     -F "intervieweeId=CANDIDATE_WITH_EXTREMELY_LONG_ID_THAT_EXCEEDS_THE_MAXIMUM_ALLOWED_LENGTH_OF_100_CHARACTERS" \
     -F "questionId=TECH_Q1"
```

```json
{
  "message": "Constraint validation failed: Interviewee ID must be between 1 and 100 characters",
  "data": {
    "constraintViolations": [
      "Interviewee ID must be between 1 and 100 characters"
    ],
    "errorType": "CONSTRAINT_VIOLATION"
  }
}
```

## 🔐 **3. Security Validation Examples**

### **Path Traversal Attack Prevention**
```bash
curl -X POST "http://localhost:8080/api/v1/video/upload" \
     -F "file=@../../../etc/passwd.mp4" \
     -F "intervieweeId=HACKER_001" \
     -F "questionId=MALICIOUS_Q1"
```

```json
{
  "message": "Invalid input: File name contains invalid path characters",
  "data": {
    "errorType": "INVALID_ARGUMENT",
    "argument": "File name contains invalid path characters",
    "securityViolation": true,
    "suggestion": "Please use a simple filename without path separators"
  }
}
```

### **Dangerous File Extension Prevention**
```bash
curl -X POST "http://localhost:8080/api/v1/video/upload" \
     -F "file=@malware.exe.mp4" \
     -F "intervieweeId=HACKER_001" \
     -F "questionId=MALICIOUS_Q1"
```

```json
{
  "message": "Video processing failed: File name contains potentially dangerous content: .exe",
  "data": {
    "errorType": "VIDEO_PROCESSING_ERROR",
    "processingStage": "high-performance-upload",
    "suggestion": "Please try with a different video file or contact support"
  }
}
```

## 📊 **4. Performance Monitoring Examples**

### **Check System Performance**
```bash
curl -X GET "http://localhost:8080/api/v1/video/performance/status"
```

### **✅ Optimal Performance Response**
```json
{
  "message": "🚀 Performance OPTIMAL: 65.2 MB/s average, 98.5% success rate",
  "data": {
    "performanceOptimal": true,
    "averageUploadSpeedMBps": 65.2,
    "performanceTarget": "50MB in <1000ms",
    "minimumSpeedRequired": 50.0,
    "systemStatus": "OPTIMAL",
    "totalUploads": 142,
    "successfulUploads": 140,
    "successRate": 0.985,
    "recommendations": [
      "System performing excellently",
      "All performance targets being met"
    ]
  }
}
```

### **⚠️ Performance Warning Response**
```json
{
  "message": "⚠️ Performance needs optimization: 32.4 MB/s average",
  "data": {
    "performanceOptimal": false,
    "averageUploadSpeedMBps": 32.4,
    "systemStatus": "NEEDS_OPTIMIZATION",
    "successRate": 0.89,
    "recommendations": [
      "Consider increasing server resources",
      "Check network connectivity",
      "Review S3 bucket configuration"
    ]
  }
}
```

## 🎬 **5. File Management Examples**

### **Check Video Exists**
```bash
curl -X GET "http://localhost:8080/api/v1/video/exists" \
     -G -d "intervieweeId=CANDIDATE_001" \
     -d "questionId=TECH_Q1" \
     -d "fileName=interview-response.mp4"
```

**Response:** `true` or `false`

### **Generate Signed URL**
```bash
curl -X GET "http://localhost:8080/api/v1/video/url" \
     -G -d "intervieweeId=CANDIDATE_001" \
     -d "questionId=TECH_Q1" \
     -d "fileName=interview-response.mp4" \
     -d "durationHours=24"
```

**Response:** 
```
https://s3.amazonaws.com/innfusion-videos/CANDIDATE_001/TECH_Q1/interview-response.mp4?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Date=20241220T120000Z&X-Amz-SignedHeaders=host&X-Amz-Expires=86400&X-Amz-Credential=...&X-Amz-Signature=...
```

### **Delete Video**
```bash
curl -X DELETE "http://localhost:8080/api/v1/video/delete" \
     -G -d "intervieweeId=CANDIDATE_001" \
     -d "questionId=TECH_Q1" \
     -d "fileName=interview-response.mp4"
```

**Response:**
```json
{
  "message": "Video deleted successfully",
  "data": {
    "deleted": true,
    "s3Key": "CANDIDATE_001/TECH_Q1/interview-response.mp4"
  }
}
```

## 🚀 **6. Batch Upload Examples**

### **Successful Batch Upload**
```bash
curl -X POST "http://localhost:8080/api/v1/video/batch-upload" \
     -F "files=@question1-response.mp4" \
     -F "files=@question2-response.mp4" \
     -F "files=@question3-response.mp4" \
     -F "intervieweeId=CANDIDATE_001" \
     -F "questionIds=TECH_Q1" \
     -F "questionIds=TECH_Q2" \
     -F "questionIds=BEHAVIORAL_Q1"
```

**Response:**
```json
{
  "message": "Batch upload completed",
  "data": {
    "totalFiles": 3,
    "intervieweeId": "CANDIDATE_001",
    "batchId": "batch_a1b2c3d4-e5f6-7g8h-9i0j-k1l2m3n4o5p6",
    "results": [
      {
        "message": "🎯 High-performance upload SUCCESS: 12MB in 650ms (18.5 MB/s)",
        "data": { "s3Key": "CANDIDATE_001/TECH_Q1/question1-response.mp4" }
      },
      {
        "message": "🎯 High-performance upload SUCCESS: 8MB in 420ms (19.0 MB/s)",
        "data": { "s3Key": "CANDIDATE_001/TECH_Q2/question2-response.mp4" }
      },
      {
        "message": "🎯 High-performance upload SUCCESS: 15MB in 780ms (19.2 MB/s)",
        "data": { "s3Key": "CANDIDATE_001/BEHAVIORAL_Q1/question3-response.mp4" }
      }
    ],
    "overallPerformance": {
      "totalSize": "35MB",
      "totalTime": "1850ms",
      "averageSpeed": "18.9 MB/s",
      "allTargetsMet": true
    }
  }
}
```

### **Batch Upload with Mismatched Arrays**
```bash
curl -X POST "http://localhost:8080/api/v1/video/batch-upload" \
     -F "files=@video1.mp4" \
     -F "intervieweeId=CANDIDATE_001" \
     -F "questionIds=Q1" \
     -F "questionIds=Q2"  # 2 question IDs for 1 file
```

**Response:**
```json
{
  "message": "Number of files must match number of question IDs",
  "data": {
    "errorType": "VALIDATION_ERROR",
    "filesCount": 1,
    "questionIdsCount": 2,
    "suggestion": "Please ensure you provide exactly one question ID for each file"
  }
}
```

## 🎯 **7. Integration with Frontend**

### **JavaScript Upload Example**
```javascript
async function uploadInterview(file, intervieweeId, questionId) {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('intervieweeId', intervieweeId);
    formData.append('questionId', questionId);
    
    try {
        const response = await fetch('/api/v1/video/upload', {
            method: 'POST',
            body: formData
        });
        
        const result = await response.json();
        
        if (response.ok) {
            console.log('✅ Upload successful:', result);
            displaySuccess(result);
        } else {
            console.error('❌ Upload failed:', result);
            displayValidationErrors(result);
        }
        
    } catch (error) {
        console.error('💥 Network error:', error);
        displayNetworkError(error);
    }
}

function displayValidationErrors(result) {
    if (result.data.errorType === 'VALIDATION_ERROR') {
        // Handle field-specific validation errors
        for (const [field, errors] of Object.entries(result.data.validationErrors)) {
            showFieldError(field, errors);
        }
    } else if (result.data.errorType === 'FILE_SIZE_EXCEEDED') {
        showFileSizeError(result.data.maxSize, result.data.actualSize);
    } else if (result.data.errorType === 'CONSTRAINT_VIOLATION') {
        showConstraintViolations(result.data.constraintViolations);
    }
}
```

### **React Component Example**
```jsx
import React, { useState } from 'react';

function VideoUploadComponent() {
    const [file, setFile] = useState(null);
    const [uploading, setUploading] = useState(false);
    const [errors, setErrors] = useState({});
    
    const handleUpload = async () => {
        setUploading(true);
        setErrors({});
        
        const formData = new FormData();
        formData.append('file', file);
        formData.append('intervieweeId', 'CANDIDATE_001');
        formData.append('questionId', 'TECH_Q1');
        
        try {
            const response = await fetch('/api/v1/video/upload', {
                method: 'POST',
                body: formData
            });
            
            const result = await response.json();
            
            if (response.ok) {
                console.log('🎯 Upload successful!', result);
                // Handle success
            } else {
                // Parse validation errors
                if (result.data.validationErrors) {
                    setErrors(result.data.validationErrors);
                } else {
                    setErrors({ general: result.message });
                }
            }
        } catch (error) {
            setErrors({ network: 'Network error occurred' });
        } finally {
            setUploading(false);
        }
    };
    
    return (
        <div className="upload-component">
            <input 
                type="file" 
                accept="video/*"
                onChange={(e) => setFile(e.target.files[0])}
            />
            
            {errors.file && (
                <div className="error">❌ {errors.file.join(', ')}</div>
            )}
            
            <button 
                onClick={handleUpload} 
                disabled={!file || uploading}
            >
                {uploading ? 'Uploading...' : 'Upload Video'}
            </button>
        </div>
    );
}
```

## 🏆 **Best Practices Summary**

### **✅ Do's**
- Always validate file size before uploading (client-side pre-check)
- Use descriptive interviewee and question IDs
- Check system performance before large batch uploads
- Handle validation errors gracefully with user-friendly messages
- Use appropriate content types (video/mp4, video/avi, etc.)
- Include progress indicators for large uploads

### **❌ Don'ts**
- Don't upload files larger than 50MB without compression
- Don't use special characters or paths in filenames
- Don't ignore validation error messages
- Don't retry failed uploads without addressing the root cause
- Don't upload non-video files to video endpoints
- Don't use extremely long IDs (>100 characters)

### **🎯 Performance Tips**
- Compress videos before uploading for better performance
- Use batch uploads for multiple files from same candidate
- Monitor performance status endpoint for system health
- Consider file format - MP4 generally uploads faster than AVI
- Upload during off-peak hours for better performance

**Your validation framework is production-ready with comprehensive error handling and user-friendly messages!** 🎉✨ 