package com.innfusion.exception;

/**
 * Exception thrown when performance thresholds are not met.
 * Used for performance monitoring and alerting.
 */
public class PerformanceException extends RuntimeException {
    
    private final String metric;
    private final Object expectedValue;
    private final Object actualValue;
    
    public PerformanceException(String message, String metric, Object expectedValue, Object actualValue) {
        super(message);
        this.metric = metric;
        this.expectedValue = expectedValue;
        this.actualValue = actualValue;
    }
    
    public PerformanceException(String message, String metric, Object expectedValue, Object actualValue, Throwable cause) {
        super(message, cause);
        this.metric = metric;
        this.expectedValue = expectedValue;
        this.actualValue = actualValue;
    }
    
    public String getMetric() {
        return metric;
    }
    
    public Object getExpectedValue() {
        return expectedValue;
    }
    
    public Object getActualValue() {
        return actualValue;
    }
} 