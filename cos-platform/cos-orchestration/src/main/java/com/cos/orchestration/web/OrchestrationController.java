package com.cos.orchestration.web;

import com.cos.domain.model.Application;
import com.cos.orchestration.process.CreditOriginationOrchestrator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Process Workbook, "Credit Origination Orchestration" tab: "Triggered by an inbound REST
 * request to the COS origination API."
 */
@RestController
@RequestMapping("/v1/applications")
public class OrchestrationController {

    private final CreditOriginationOrchestrator orchestrator;

    public OrchestrationController(CreditOriginationOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping("/originate")
    public ResponseEntity<Application> originate(@RequestBody Application application) {
        return ResponseEntity.ok(orchestrator.originate(application));
    }
}
