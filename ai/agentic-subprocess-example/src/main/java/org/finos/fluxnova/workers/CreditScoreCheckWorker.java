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
 * Mock worker for the "credit-score-check" external task topic.
 *
 *   CUST-101  Robert Chen   — long, clean credit history; excellent score
 *   CUST-202  Marcus Webb   — reasonable score; nothing alarming on credit alone
 *   CUST-303  Dmitri Volkov — very poor score, multiple defaults
 */
@Component
public class CreditScoreCheckWorker {

    private static final Logger log = LoggerFactory.getLogger(CreditScoreCheckWorker.class);
    private static final String TOPIC     = "credit-score-check";
    private static final String WORKER_ID = "mock-" + TOPIC;
    private static final long   LOCK_MS   = 20_000L;

    private final ExternalTaskService externalTaskService;

    public CreditScoreCheckWorker(ExternalTaskService externalTaskService) {
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
        log.info("\n┌─ Credit Score Check ──────────────────────────────────────\n"
                + "│  Customer  : {}\n"
                + "│  Score     : {}\n"
                + "│  Risk Band : {}\n"
                + "└──────────────────────────────────────────────────────────",
                customerId, result.get("creditScore"), result.get("creditRiskBand"));
    }

    private Map<String, Object> buildResult(String customerId) {
        return switch (customerId) {
            case "CUST-101" -> Map.of(   // Robert: excellent, long clean history
                    "creditScore",    810,
                    "creditRiskBand", "LOW");
            case "CUST-202" -> Map.of(   // Marcus: reasonable — credit alone raises no alarm
                    "creditScore",    701,
                    "creditRiskBand", "MEDIUM");
            case "CUST-303" -> Map.of(   // Dmitri: very poor, multiple defaults
                    "creditScore",    401,
                    "creditRiskBand", "HIGH");
            default -> Map.of(
                    "creditScore",    620,
                    "creditRiskBand", "MEDIUM");
        };
    }

    private void simulateWork() {
        log.info("[{}] Contacting credit bureau — this takes ~5 s ...", TOPIC);
        try {
            Thread.sleep(5_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
