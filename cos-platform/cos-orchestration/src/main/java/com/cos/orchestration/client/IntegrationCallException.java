package com.cos.orchestration.client;

/** Raised once a service call's own retry policy is exhausted, or a 4xx was returned. */
public class IntegrationCallException extends RuntimeException {

    private final String serviceName;
    private final String code;
    private final int retryCount;

    public IntegrationCallException(String serviceName, String code, String message, int retryCount) {
        super(message);
        this.serviceName = serviceName;
        this.code = code;
        this.retryCount = retryCount;
    }

    public IntegrationCallException(String serviceName, String code, String message, int retryCount, Throwable cause) {
        super(message, cause);
        this.serviceName = serviceName;
        this.code = code;
        this.retryCount = retryCount;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getCode() {
        return code;
    }

    public int getRetryCount() {
        return retryCount;
    }
}
