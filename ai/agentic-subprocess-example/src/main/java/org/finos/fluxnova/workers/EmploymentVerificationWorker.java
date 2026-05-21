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
 * Mock worker for the "employment-verification" external task topic.
 *
 *   CUST-101  Robert Chen   — confirmed employed, high salary
 *   CUST-202  Marcus Webb   — self-employed; no employer to contact (expected, not a red flag)
 *   CUST-303  Dmitri Volkov — employer contact failed; not verified
 */
@Component
public class EmploymentVerificationWorker {

    private static final Logger log = LoggerFactory.getLogger(EmploymentVerificationWorker.class);
    private static final String TOPIC     = "employment-verification";
    private static final String WORKER_ID = "mock-" + TOPIC;
    private static final long   LOCK_MS   = 20_000L;

    private final ExternalTaskService externalTaskService;

    public EmploymentVerificationWorker(ExternalTaskService externalTaskService) {
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
        String customerId = (String) task.getVariables().get("customerId");
        log.info("[{}] Fetched task {} — customerId={}", TOPIC, task.getId(), customerId);

        simulateWork();

        Map<String, Object> result = buildResult(customerId);
        externalTaskService.complete(task.getId(), WORKER_ID, result);
        log.info("\n┌─ Employment Verification ──────────────────────────────────\n"
                + "│  Customer            : {}\n"
                + "│  Employment Verified : {}\n"
                + "│  Annual Income       : £{}\n"
                + "└────────────────────────────────────────────────────────────",
                customerId, result.get("employmentVerified"),
                String.format("%,d", (Number) result.get("annualIncome")));
    }

    private Map<String, Object> buildResult(String customerId) {
        return switch (customerId) {
            case "CUST-101" -> Map.of(   // Robert: confirmed, senior banking role
                    "employmentVerified", true,
                    "annualIncome",       120_000);
            case "CUST-202" -> Map.of(   // Marcus: self-employed, no employer to verify
                    "employmentVerified", false,
                    "annualIncome",       0);
            case "CUST-303" -> Map.of(   // Dmitri: employer unreachable
                    "employmentVerified", false,
                    "annualIncome",       0);
            default -> Map.of(
                    "employmentVerified", false,
                    "annualIncome",       0);
        };
    }

    private void simulateWork() {
        log.info("[{}] Contacting employer verification bureau — this takes ~5 s ...", TOPIC);
        try {
            Thread.sleep(5_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
