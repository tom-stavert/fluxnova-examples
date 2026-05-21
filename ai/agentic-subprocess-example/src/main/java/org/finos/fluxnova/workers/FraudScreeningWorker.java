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
 * Mock worker for the "fraud-screening" external task topic.
 *
 *   APP-001  Robert Chen   — clean, long-standing customer; very low risk
 *   APP-002  Marcus Webb   — bank statement anomalies flagged; cash flow pattern
 *                           matches known money-laundering profiles
 *   APP-003  Dmitri Volkov — no specific fraud indicators; just a poor financial profile
 */
@Component
public class FraudScreeningWorker {

    private static final Logger log = LoggerFactory.getLogger(FraudScreeningWorker.class);
    private static final String TOPIC     = "fraud-screening";
    private static final String WORKER_ID = "mock-" + TOPIC;
    private static final long   LOCK_MS   = 20_000L;

    private final ExternalTaskService externalTaskService;

    public FraudScreeningWorker(ExternalTaskService externalTaskService) {
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
        String customerId    = (String) task.getVariables().get("customerId");
        log.info("[{}] Fetched task {} — applicationId={} customerId={}",
                TOPIC, task.getId(), applicationId, customerId);

        simulateWork();

        Map<String, Object> result = buildResult(applicationId);
        externalTaskService.complete(task.getId(), WORKER_ID, result);
        log.info("\n┌─ Fraud Screening ──────────────────────────────────────────\n"
                + "│  Application     : {}\n"
                + "│  Customer        : {}\n"
                + "│  Fraud Risk Score: {} / 100\n"
                + "└────────────────────────────────────────────────────────────",
                applicationId, customerId, result.get("fraudRiskScore"));
    }

    private Map<String, Object> buildResult(String applicationId) {
        return switch (applicationId) {
            case "APP-001" -> Map.of("fraudRiskScore", 5);    // Robert: clean, trusted customer
            case "APP-002" -> Map.of("fraudRiskScore", 81);   // Marcus: cash flow matches ML patterns
            case "APP-003" -> Map.of("fraudRiskScore", 28);   // Dmitri: no fraud, just poor finances
            default        -> Map.of("fraudRiskScore", 50);
        };
    }

    private void simulateWork() {
        log.info("[{}] Running watchlist and behavioural analytics — this takes ~5 s ...", TOPIC);
        try {
            Thread.sleep(5_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
