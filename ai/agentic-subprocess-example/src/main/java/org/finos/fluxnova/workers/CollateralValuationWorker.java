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
 * Mock worker for the "collateral-valuation" external task topic.
 *
 *   APP-001  Robert Chen   — residential flat, central London
 *   APP-002  Marcus Webb   — no collateral declared
 *   APP-003  Dmitri Volkov — no collateral declared
 */
@Component
public class CollateralValuationWorker {

    private static final Logger log = LoggerFactory.getLogger(CollateralValuationWorker.class);
    private static final String TOPIC     = "collateral-valuation";
    private static final String WORKER_ID = "mock-" + TOPIC;
    private static final long   LOCK_MS   = 20_000L;

    private final ExternalTaskService externalTaskService;

    public CollateralValuationWorker(ExternalTaskService externalTaskService) {
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

        Map<String, Object> result = buildResult(applicationId);
        externalTaskService.complete(task.getId(), WORKER_ID, result);
        log.info("\n┌─ Collateral Valuation ─────────────────────────────────────\n"
                + "│  Application  : {}\n"
                + "│  Market Value : £{}\n"
                + "└────────────────────────────────────────────────────────────",
                applicationId, String.format("%,d", (Number) result.get("collateralValue")));
    }

    private Map<String, Object> buildResult(String applicationId) {
        return switch (applicationId) {
            case "APP-001" -> Map.of("collateralValue", 380_000); // Robert: 2-bed flat, central London
            case "APP-002" -> Map.of("collateralValue", 0);       // Marcus: no collateral
            case "APP-003" -> Map.of("collateralValue", 0);       // Dmitri: no collateral
            default        -> Map.of("collateralValue", 0);
        };
    }

    private void simulateWork() {
        log.info("[{}] Requesting independent market valuation — this takes ~5 s ...", TOPIC);
        try {
            Thread.sleep(5_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
