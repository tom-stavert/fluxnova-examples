package org.finos.fluxnova;

import org.finos.fluxnova.bpm.engine.RuntimeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Seeds loan application process instances on startup so the agentic subprocess
 * can be exercised immediately.
 *
 * Each instance lands on the "Validate Application" user task —
 * complete it via the Fluxnova Tasklist (or REST) to hand off to the AI agent.
 */
@Component
public class LoanApplicationSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(LoanApplicationSeeder.class);

    private static final String PROCESS_KEY = "loanAssessmentProcess";

    /**
     * APP-001  Robert Chen — High-earning banker, excellent credit, long clean history,
     *          offering a London flat as collateral. Straightforwardly low risk.
     *          Expected outcome: APPROVE
     *
     * APP-002  Marcus Webb — Self-employed consultant with reasonable credit. Bank statements
     *          reveal large unexplained cash flows inconsistent with his stated business
     *          income, triggering a high fraud risk score.
     *          Expected outcome: REJECT
     *
     * APP-003  Dmitri Volkov — Very poor credit, no verifiable income, no collateral,
     *          loan repayments clearly unaffordable. Straightforward financial rejection.
     *          Expected outcome: REJECT
     */
    private static final List<Map<String, Object>> SEED_APPLICATIONS = List.of(

        // APP-001 — Obvious approval
        Map.of(
            "applicationId",          "APP-001",
            "customerId",             "CUST-101",
            "applicantName",          "Robert Chen",
            "requestedAmount",        320000,
            "applicantType",          "SALARIED",
            "hasCollateral",          true,
            "collateralDescription",  "Residential flat, London",
            "loanPurpose",            "Residential property purchase."
        ),

        // APP-002 — Looks reasonable; bank statements reveal fraud indicators
        Map.of(
            "applicationId",   "APP-002",
            "customerId",      "CUST-202",
            "applicantName",   "Marcus Webb",
            "requestedAmount", 75000,
            "applicantType",   "SELF_EMPLOYED",
            "hasCollateral",   false,
            "loanPurpose",     "Business expansion."
        ),

        // APP-003 — Obvious rejection on financial grounds
        Map.of(
            "applicationId",   "APP-003",
            "customerId",      "CUST-303",
            "applicantName",   "Dmitri Volkov",
            "requestedAmount", 95000,
            "applicantType",   "SALARIED",
            "hasCollateral",   false,
            "loanPurpose",     "Debt consolidation."
        )
    );

    private final RuntimeService runtimeService;

    public LoanApplicationSeeder(RuntimeService runtimeService) {
        this.runtimeService = runtimeService;
    }

    @Override
    public void run(String... args) {
        log.info("Seeding {} loan application process instances...", SEED_APPLICATIONS.size());

        for (Map<String, Object> variables : SEED_APPLICATIONS) {
            String applicationId = (String) variables.get("applicationId");
            String businessKey = applicationId + "-" + UUID.randomUUID().toString().substring(0, 8);

            runtimeService.startProcessInstanceByKey(PROCESS_KEY, businessKey, variables);

            log.info("Started process instance  applicationId={} businessKey={} requestedAmount={}",
                applicationId, businessKey, variables.get("requestedAmount"));
        }

        log.info("Seeding complete. Complete the 'Validate Application' user tasks in the Tasklist to hand off to the agent.");
    }
}
