package com.cos.orchestration.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class OrchestrationConfig {

    /** Process Workbook, "Gather External Data" tab: "Bounded concurrency of 5". */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService bureauCallExecutor() {
        return Executors.newFixedThreadPool(5);
    }
}
