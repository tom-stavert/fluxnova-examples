package org.finos.fluxnova.workers;

import org.finos.fluxnova.bpm.engine.ExternalTaskService;
import org.finos.fluxnova.bpm.engine.externaltask.LockedExternalTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Mock worker for the "generate-assessment-report" external task topic.
 *
 * Compiles all gathered evidence into a structured narrative report and
 * derives the recommended lending decision from the data present on the task.
 *
 * Decision logic (intentionally simple for a mock — the real decision was
 * already made by the agent; this worker just formats it):
 *   - fraudRiskScore > 70           → REJECT
 *   - creditRiskBand == HIGH
 *     AND employmentVerified != true → REJECT
 *   - otherwise                     → APPROVE
 */
@Component
public class GenerateAssessmentReportWorker {

    private static final Logger log = LoggerFactory.getLogger(GenerateAssessmentReportWorker.class);
    private static final String TOPIC     = "generate-assessment-report";
    private static final String WORKER_ID = "mock-" + TOPIC;
    private static final long   LOCK_MS   = 20_000L;

    private final ExternalTaskService externalTaskService;

    public GenerateAssessmentReportWorker(ExternalTaskService externalTaskService) {
        this.externalTaskService = externalTaskService;
    }

    @Scheduled(fixedDelay = 3_000)
    public void poll() {
        List<LockedExternalTask> tasks = externalTaskService
                .fetchAndLock(1, WORKER_ID)
                .topic(TOPIC, LOCK_MS)
                .execute();

        for (LockedExternalTask task : tasks) {
            process(task);
        }
    }

    private void process(LockedExternalTask task) {
        String applicationId = (String) task.getVariables().get("applicationId");
        log.info("[{}] Fetched task {} — applicationId={}", TOPIC, task.getId(), applicationId);

        simulateWork();

        Map<String, Object> vars = task.getVariables();
        String decision    = deriveDecision(vars);
        String report      = buildReport(vars, decision);

        externalTaskService.complete(task.getId(), WORKER_ID,
                Map.of("assessmentReport",    report,
                       "recommendedDecision", decision));

        log.info("[{}] Assessment report for {}:\n{}", TOPIC, applicationId, report);
    }

    // -------------------------------------------------------------------------

    private String deriveDecision(Map<String, Object> vars) {
        int     fraudScore         = toInt(vars.get("fraudRiskScore"), 0);
        String  creditBand         = toString(vars.get("creditRiskBand"), "MEDIUM");
        boolean employmentVerified = toBoolean(vars.get("employmentVerified"), false);
        boolean affordabilityOk    = toBoolean(vars.get("affordabilityPassed"), true); // default pass if not run

        if (fraudScore > 70) {
            return "REJECT";
        }
        if ("HIGH".equals(creditBand) && !employmentVerified) {
            return "REJECT";
        }
        if (!affordabilityOk) {
            return "REJECT";
        }
        return "APPROVE";
    }

    private String buildReport(Map<String, Object> vars, String decision) {
        String appId    = toString(vars.get("applicationId"),  "N/A");
        String custId   = toString(vars.get("customerId"),     "N/A");
        String name     = toString(vars.get("applicantName"),  "Unknown");
        int    amount   = toInt(vars.get("requestedAmount"),   0);
        int    score    = toInt(vars.get("creditScore"),       0);
        String band     = toString(vars.get("creditRiskBand"), "N/A");
        int    income   = toInt(vars.get("annualIncome"),      0);
        int    fraud    = toInt(vars.get("fraudRiskScore"),    -1);
        int    colVal   = toInt(vars.get("collateralValue"),   0);
        double dti      = toDouble(vars.get("debtToIncomeRatio"), -1.0);
        int    bsScore  = toInt(vars.get("bankStatementScore"), -1);
        boolean empVer  = toBoolean(vars.get("employmentVerified"), false);

        StringBuilder sb = new StringBuilder();
        sb.append("LOAN ASSESSMENT REPORT\n");
        sb.append("======================\n");
        sb.append(String.format("Application : %s%n", appId));
        sb.append(String.format("Customer    : %s (%s)%n", name, custId));
        sb.append(String.format("Requested   : £%,d%n%n", amount));

        sb.append("CREDIT\n");
        sb.append(String.format("  Score       : %d%n", score));
        sb.append(String.format("  Risk band   : %s%n%n", band));

        sb.append("INCOME VERIFICATION\n");
        if (empVer) {
            sb.append(String.format("  Employment verified : yes%n"));
            sb.append(String.format("  Annual income       : £%,d%n%n", income));
        } else if (bsScore >= 0) {
            sb.append(String.format("  Bank statement score : %d / 100%n", bsScore));
            sb.append(String.format("  Derived income       : £%,d p.a.%n%n", income));
        } else {
            sb.append("  Income could not be verified.%n%n");
        }

        sb.append("FRAUD SCREENING\n");
        if (fraud >= 0) {
            sb.append(String.format("  Risk score : %d / 100%n%n", fraud));
        } else {
            sb.append("  Not run.%n%n");
        }

        if (colVal > 0) {
            sb.append("COLLATERAL\n");
            sb.append(String.format("  Market value : £%,d%n%n", colVal));
        }

        if (dti >= 0) {
            sb.append("AFFORDABILITY\n");
            sb.append(String.format("  Debt-to-income ratio : %.2f%n%n", dti));
        }

        sb.append("RECOMMENDATION\n");
        sb.append(String.format("  Decision : %s%n", decision));

        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Safe type-coercion helpers

    private int toInt(Object v, int fallback) {
        if (v instanceof Number n) return n.intValue();
        return fallback;
    }

    private double toDouble(Object v, double fallback) {
        if (v instanceof Number n) return n.doubleValue();
        return fallback;
    }

    private boolean toBoolean(Object v, boolean fallback) {
        if (v instanceof Boolean b) return b;
        return fallback;
    }

    private String toString(Object v, String fallback) {
        return v != null ? v.toString() : fallback;
    }

    private void simulateWork() {
        // Report generation is instant — no simulated delay needed
    }
}
