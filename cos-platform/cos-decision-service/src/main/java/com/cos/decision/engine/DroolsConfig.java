package com.cos.decision.engine;

import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Loads every decision table / DRL file under src/main/resources (via META-INF/kmodule.xml) into
 * a single KieContainer, built once at startup. KieSessions are created per-evaluation from this
 * container - see PartyRuleEngine.
 */
@Configuration
public class DroolsConfig {

    @Bean
    public KieContainer kieContainer() {
        return KieServices.Factory.get().getKieClasspathContainer();
    }
}
