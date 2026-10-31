package com.cos.orchestration.client;

import com.cos.domain.enums.ErrorSource;
import com.cos.domain.model.Application;
import com.cos.domain.model.ApplicationError;

import java.time.LocalDateTime;
import java.util.ArrayList;

/** Builds and appends {@code ApplicationError} records (General Principles tab). */
public final class ApplicationErrors {

    private ApplicationErrors() {
    }

    public static void append(Application application, ApplicationError error) {
        if (application.getErrors() == null) {
            application.setErrors(new ArrayList<>());
        }
        application.getErrors().add(error);
    }

    public static ApplicationError fromIntegrationFailure(IntegrationCallException e) {
        return ApplicationError.builder()
                .code(e.getCode())
                .message(e.getMessage())
                .source(ErrorSource.Integration)
                .serviceName(e.getServiceName())
                .retryCount(e.getRetryCount())
                .isResolved(Boolean.FALSE)
                .occurredOn(LocalDateTime.now())
                .build();
    }
}
