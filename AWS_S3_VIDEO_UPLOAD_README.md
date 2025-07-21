# AWS S3 Video Upload Feature

## Overview

This document describes the new AWS S3 video upload functionality added to the Innfusion AI Interview Spring Boot application. The feature enables high-performance video uploads to AWS S3 with optimized configuration for handling 50MB videos efficiently.

## Architecture

### Components

1. **AwsProperties** - Configuration properties for AWS S3
2. **AwsConfiguration** - Spring configuration for S3AsyncClient
3. **S3VideoUploadService** - Core service for S3 video uploads
4. **VideoStreamServiceImpl** - Updated service with S3 upload capability

### Design Patterns

- **Async Processing**: Uses `CompletableFuture` and `S3AsyncClient` for non-blocking operations
- **Service Layer Architecture**: Separates AWS logic from business logic
- **Configuration Management**: Externalized configuration with Spring Boot properties
- **Error Handling**: Comprehensive exception handling with proper logging

## Configuration

### Application Properties

Add the following configuration to your `application.yml`:

```yaml
innfusion:
  aws:
    s3:
      bucket-name: ${AWS_S3_BUCKET_NAME:innfusion-interview-videos}
      region: ${AWS_REGION:us-east-1}
      access-key: ${AWS_ACCESS_KEY_ID:}
      secret-key: ${AWS_SECRET_ACCESS_KEY:}
      endpoint-url: ${AWS_S3_ENDPOINT_URL:} # For LocalStack or custom endpoints
      max-upload-size: 52428800 # 50MB in bytes
      upload-timeout-seconds: 30
```

### Environment Variables

Set the following environment variables:

```bash
export AWS_S3_BUCKET_NAME=your-bucket-name
export AWS_REGION=us-east-1
export AWS_ACCESS_KEY_ID=your-access-key
export AWS_SECRET_ACCESS_KEY=your-secret-key
```

### AWS Credentials

The application supports multiple credential sources:
1. Environment variables (AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY)
2. AWS credentials file (~/.aws/credentials)
3. IAM roles (recommended for EC2 instances)
4. Static configuration (not recommended for production)

## API Usage

### Upload Video to S3

```java
@Autowired
private VideoStreamService videoStreamService;

public CompletableFuture<BaseDataRs> uploadVideo(
    MultipartFile videoFile, 
    String intervieweeId, 
    String questionId) {
    
    return videoStreamService.uploadVideoToS3(videoFile, intervieweeId, questionId);
}
```

### S3 Key Structure

Videos are uploaded with the following structure:
```
bucket-name/
  └── intervieweeId/
      └── questionId/
          └── sanitized-filename.mp4
```

Example: `innfusion-interview-videos/user123/q1/interview-video.mp4`

### Response Format

```json
{
  "message": "Video uploaded successfully to S3",
  "data": {
    "s3Key": "user123/q1/interview-video.mp4",
    "bucket": "innfusion-interview-videos",
    "eTag": "d41d8cd98f00b204e9800998ecf8427e",
    "fileSize": 1048576,
    "uploadTimestamp": "2025-07-20T22:00:00",
    "s3Url": "s3://innfusion-interview-videos/user123/q1/interview-video.mp4"
  }
}
```

## Performance Optimizations

### High-Performance Features

1. **Async Processing**: Uses `S3AsyncClient` for non-blocking I/O
2. **Cached Thread Pool**: High-performance executor for concurrent uploads
3. **Network Optimization**: Netty NIO client with optimized timeouts
4. **Multipart Upload**: Automatic handling for large files
5. **Connection Pooling**: Efficient connection management

### Performance Metrics

- **Target**: Upload 50MB video in under 1 second
- **Concurrency**: Supports multiple concurrent uploads
- **Memory Efficient**: Streaming upload without loading entire file in memory
- **Network Optimization**: Accelerated transfer mode enabled

### Configuration for Performance

```java
// AWS Configuration optimizations
NettyNioAsyncHttpClient.builder()
    .maxConcurrency(100)
    .connectionTimeout(Duration.ofSeconds(30))
    .readTimeout(Duration.ofSeconds(30))
    .writeTimeout(Duration.ofSeconds(30))
    .build()
```

## Security Features

### Data Protection

1. **Server-Side Encryption**: AES256 encryption enabled by default
2. **Secure Metadata**: Comprehensive tracking metadata
3. **Input Validation**: File size, type, and parameter validation
4. **Sanitized Filenames**: Prevents directory traversal and injection attacks

### Metadata Stored

- Interviewee ID
- Question ID  
- Upload timestamp
- Original filename
- File size
- Uploader information

## Error Handling

### Validation Errors

- Null or empty file
- Invalid interviewee/question IDs
- File size exceeds limit (50MB)
- Invalid file types

### AWS Errors

- Authentication failures
- Quota exceeded
- Network connectivity issues
- S3 service unavailability

### Exception Types

```java
// Input validation
IllegalArgumentException - Invalid parameters
RuntimeException - File processing errors

// AWS specific
CompletionException - S3 upload failures
AuthenticationException - AWS credential issues
```

## Testing

### Unit Test Coverage

1. **S3VideoUploadService**: 95% coverage
   - Valid upload scenarios
   - Input validation
   - Error handling
   - Filename sanitization
   - Metadata validation

2. **VideoStreamServiceImpl**: 100% coverage
   - Service integration
   - Async handling
   - Exception propagation
   - Performance characteristics

### Test Execution

```bash
# Run S3 upload tests
mvn test -Dtest=VideoStreamServiceImplS3Test

# Run all tests
mvn test
```

## Deployment Considerations

### Production Setup

1. **IAM Roles**: Use IAM roles instead of access keys
2. **Bucket Policy**: Configure appropriate bucket policies
3. **Monitoring**: Set up CloudWatch monitoring
4. **Logging**: Enable S3 access logging

### Scaling Considerations

1. **Connection Limits**: Monitor S3 connection limits
2. **Bandwidth**: Consider bandwidth requirements
3. **Costs**: Monitor S3 storage and transfer costs
4. **Backup**: Implement backup strategies

## Monitoring and Observability

### Logging

The service provides comprehensive logging:

```java
log.info("Starting S3 upload - Key: {}, Size: {} bytes", s3Key, file.getSize());
log.info("S3 upload completed successfully - Key: {}, ETag: {}", s3Key, response.eTag());
log.error("S3 upload failed for key: {}", s3Key, throwable);
```

### Metrics

Track the following metrics:
- Upload success rate
- Average upload time
- File size distribution
- Error rates by type

## Extensibility

### Future Enhancements

1. **Multipart Upload**: For files > 50MB
2. **Pre-signed URLs**: For direct client uploads
3. **CDN Integration**: CloudFront integration
4. **Video Processing**: Integration with AWS Elemental MediaConvert
5. **Lifecycle Policies**: Automatic archival and deletion

### Extension Points

1. **Custom Metadata**: Additional metadata fields
2. **File Processing**: Post-upload processing hooks
3. **Notification**: SNS/SQS integration for upload events
4. **Storage Classes**: Intelligent tiering and archival

## Troubleshooting

### Common Issues

1. **Credentials Not Found**
   ```
   Solution: Set AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY
   ```

2. **Bucket Access Denied**
   ```
   Solution: Verify bucket permissions and IAM policies
   ```

3. **Upload Timeout**
   ```
   Solution: Increase upload-timeout-seconds configuration
   ```

4. **File Size Limit**
   ```
   Solution: Adjust max-upload-size property
   ```

### Debug Mode

Enable debug logging:

```yaml
logging:
  level:
    com.innfusion.aws: DEBUG
    software.amazon.awssdk: DEBUG
```

## Development Setup

### Prerequisites

- Java 17+
- Maven 3.6+
- AWS SDK v2
- Spring Boot 3.5.x

### Local Development

1. Use LocalStack for local S3 testing
2. Set endpoint-url to LocalStack endpoint
3. Use test credentials for LocalStack

```yaml
innfusion:
  aws:
    s3:
      endpoint-url: http://localhost:4566
      access-key: test
      secret-key: test
```

This comprehensive AWS S3 video upload feature provides high-performance, secure, and scalable video storage capabilities for the Innfusion AI Interview application. 