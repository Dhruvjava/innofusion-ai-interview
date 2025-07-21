package com.innfusion.video.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Ultra-High-Performance Video Processing Configuration
 * 
 * Java 21 Ready with Virtual Threads Support:
 * - Detects Java version and uses Virtual Threads if available
 * - Falls back to highly optimized thread pools for Java 17+
 * - Designed for massive concurrency and sub-second performance
 */
@Configuration
@EnableAsync
@Slf4j
public class VideoPerformanceConfiguration {

    @Value("${innfusion.video.performance.virtual-threads.enabled:true}")
    private boolean virtualThreadsEnabled;

    /**
     * ULTRA-HIGH-PERFORMANCE Video Upload Executor
     * Auto-detects Java 21 Virtual Threads or uses optimized thread pool
     */
    @Bean(name = "videoUploadExecutor")
    @Primary
    public Executor videoUploadExecutor() {
        // Try to use Virtual Threads if Java 21+ is available
        if (virtualThreadsEnabled && isVirtualThreadsSupported()) {
            try {
                log.info("🚀⚡ JAVA 21+ DETECTED - Enabling VIRTUAL THREADS for unlimited concurrency!");
                return createVirtualThreadExecutor("video-virtual");
            } catch (Exception e) {
                log.warn("⚠️ Virtual Threads failed to initialize: {}", e.getMessage());
            }
        }
        
        // Fallback to ULTRA-OPTIMIZED traditional thread pool
        log.info("🚀 Using ULTRA-OPTIMIZED thread pool for video operations");
        return createUltraOptimizedThreadPool("VideoUpload", 50, 200, 1000);
    }

    /**
     * High-Performance Async Executor
     */
    @Bean(name = "asyncExecutor")
    public Executor asyncExecutor() {
        if (virtualThreadsEnabled && isVirtualThreadsSupported()) {
            try {
                return createVirtualThreadExecutor("async-virtual");
            } catch (Exception e) {
                log.debug("Falling back to traditional threads for async executor");
            }
        }
        return createUltraOptimizedThreadPool("Async", 20, 100, 500);
    }

    /**
     * Performance Monitor Executor
     */
    @Bean(name = "performanceMonitorExecutor")
    public Executor performanceMonitorExecutor() {
        if (virtualThreadsEnabled && isVirtualThreadsSupported()) {
            try {
                return createVirtualThreadExecutor("monitor-virtual");
            } catch (Exception e) {
                log.debug("Falling back to traditional threads for monitor executor");
            }
        }
        return createUltraOptimizedThreadPool("Monitor", 5, 20, 100);
    }

    /**
     * Create Virtual Thread Executor using reflection for Java 21 compatibility
     */
    private Executor createVirtualThreadExecutor(String namePrefix) {
        try {
            // Use reflection to maintain Java 17 compatibility
            var executorsClass = Executors.class;
            var method = executorsClass.getMethod("newVirtualThreadPerTaskExecutor");
            Executor executor = (Executor) method.invoke(null);
            log.info("✅ Virtual Thread Executor '{}' initialized - UNLIMITED CONCURRENCY", namePrefix);
            return executor;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create Virtual Thread Executor", e);
        }
    }

    /**
     * Create ultra-optimized traditional thread pool with aggressive settings
     */
    private Executor createUltraOptimizedThreadPool(String namePrefix, int coreSize, int maxSize, int queueSize) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // ULTRA-AGGRESSIVE settings for maximum performance
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueSize);
        executor.setKeepAliveSeconds(5);  // Quick thread reuse
        executor.setAllowCoreThreadTimeOut(false);  // Keep core threads alive
        executor.setThreadNamePrefix(namePrefix + "-");
        
        // CALLER_RUNS policy ensures no request is dropped
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        
        // Pre-start all core threads for instant availability
        executor.initialize();
        executor.getThreadPoolExecutor().prestartAllCoreThreads();
        
        log.info("🚀 ULTRA-OPTIMIZED {} thread pool: core={}, max={}, queue={}", 
                namePrefix, coreSize, maxSize, queueSize);
        
        return executor;
    }

    /**
     * Check if Virtual Threads are supported (Java 21+)
     */
    private boolean isVirtualThreadsSupported() {
        try {
            // Check Java version
            String javaVersion = System.getProperty("java.version");
            int majorVersion = getMajorVersion(javaVersion);
            
            if (majorVersion >= 21) {
                // Check if Virtual Thread methods exist
                Executors.class.getMethod("newVirtualThreadPerTaskExecutor");
                log.debug("✅ Java {} detected - Virtual Threads supported", majorVersion);
                return true;
            } else {
                log.debug("⚠️ Java {} detected - Virtual Threads require Java 21+", majorVersion);
                return false;
            }
        } catch (Exception e) {
            log.debug("Virtual Threads not available: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Extract major Java version number
     */
    private int getMajorVersion(String version) {
        try {
            if (version.startsWith("1.")) {
                return Integer.parseInt(version.substring(2, 3));
            } else {
                String[] parts = version.split("\\.");
                return Integer.parseInt(parts[0]);
            }
        } catch (Exception e) {
            return 17; // Safe fallback
        }
    }
} 