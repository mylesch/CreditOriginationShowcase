package com.cos.decision.web;

import com.cos.decision.engine.DecisionRulesService;
import com.cos.domain.model.Application;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Implements the Call-DRS process tab's contract: {@code POST /v1/applications/evaluate}.
 *
 * <p>{@code X-Chaos-Inject} is a test-only fault-injection hook (gated behind
 * {@code cos.chaos.enabled}, off by default) so the orchestration service's own DRS
 * error-handling (Process Workbook, "Call Decision Rules Service (DRS)" tab, Gateway 1) can be
 * exercised without a proxy in front of DRS: {@code X-Chaos-Inject: 500} returns that HTTP
 * status instead of evaluating; {@code X-Chaos-Inject: timeout} sleeps past the orchestrator's
 * read timeout.
 */
@RestController
@RequestMapping("/v1/applications")
public class DecisionController {

    private final DecisionRulesService decisionRulesService;
    private final boolean chaosEnabled;

    public DecisionController(DecisionRulesService decisionRulesService,
                               @Value("${cos.chaos.enabled:false}") boolean chaosEnabled) {
        this.decisionRulesService = decisionRulesService;
        this.chaosEnabled = chaosEnabled;
    }

    @PostMapping("/evaluate")
    public ResponseEntity<Application> evaluate(@RequestBody Application application,
                                                  @RequestHeader(value = "X-Chaos-Inject", required = false) String chaosInject) {
        if (chaosEnabled && chaosInject != null) {
            applyChaos(chaosInject);
        }
        return ResponseEntity.ok(decisionRulesService.evaluate(application));
    }

    private void applyChaos(String directive) {
        if ("timeout".equalsIgnoreCase(directive)) {
            try {
                Thread.sleep(30_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return;
        }
        throw new ResponseStatusException(HttpStatusCode.valueOf(Integer.parseInt(directive)), "Chaos injected");
    }
}
