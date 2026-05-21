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
 * Mock worker for the "bank-statement-analysis" external task topic.
 *
 *   CUST-101  Robert Chen   — large consistent salary deposits, strong savings
 *   CUST-202  Marcus Webb   — stated business income doesn't match account activity;
 *                             large irregular cash deposits of unclear origin
 *   CUST-303  Dmitri Volkov — very low, sporadic deposits; no stable income pattern
 */
@Component
public class BankStatementAnalysisWorker {

    private static final Logger log = LoggerFactory.getLogger(BankStatementAnalysisWorker.class);
    private static final String TOPIC     = "bank-statement-analysis";
    private static final String WORKER_ID = "mock-" + TOPIC;
    private static final long   LOCK_MS   = 20_000L;

    private final ExternalTaskService externalTaskService;

    public BankStatementAnalysisWorker(ExternalTaskService externalTaskService) {
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
        log.info("\n┌─ Bank Statement Analysis ──────────────────────────────────\n"
                + "│  Customer             : {}\n"
                + "│  Statement Score      : {} / 100\n"
                + "│  Derived Annual Income: £{}\n"
                + "└────────────────────────────────────────────────────────────",
                customerId, result.get("bankStatementScore"),
                String.format("%,d", (Number) result.get("annualIncome")));
    }

    private Map<String, Object> buildResult(String customerId) {
        return switch (customerId) {
            case "CUST-101" -> Map.of(   // Robert: large consistent deposits, strong savings
                    "bankStatementScore", 91,
                    "annualIncome",       120_000);
            case "CUST-202" -> Map.of(   // Marcus: irregular cash deposits inconsistent with
                    "bankStatementScore", 24, //         stated business activity
                    "annualIncome",       38_000);
            case "CUST-303" -> Map.of(   // Dmitri: sporadic, very low deposits
                    "bankStatementScore", 31,
                    "annualIncome",       14_000);
            default -> Map.of(
                    "bankStatementScore", 50,
                    "annualIncome",       30_000);
        };
    }

    private void simulateWork() {
        log.info("[{}] Parsing 12 months of transaction data — this takes ~5 s ...", TOPIC);
        try {
            Thread.sleep(5_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
