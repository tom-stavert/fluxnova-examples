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
 * Mock worker for the "affordability-assessment" external task topic.
 *
 *   APP-001  Robert Chen   — £320k on £120k salary; well within tolerance
 *   APP-002  Marcus Webb   — £38k income is a stretch but passes; fraud is the real problem
 *   APP-003  Dmitri Volkov — £14k income vs £95k loan; clearly unaffordable
 */
@Component
public class AffordabilityAssessmentWorker {

    private static final Logger log = LoggerFactory.getLogger(AffordabilityAssessmentWorker.class);
    private static final String TOPIC     = "affordability-assessment";
    private static final String WORKER_ID = "mock-" + TOPIC;
    private static final long   LOCK_MS   = 20_000L;

    private final ExternalTaskService externalTaskService;

    public AffordabilityAssessmentWorker(ExternalTaskService externalTaskService) {
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
        log.info("\n┌─ Affordability Assessment ─────────────────────────────────\n"
                + "│  Application         : {}\n"
                + "│  Debt-to-Income Ratio: {}\n"
                + "│  Affordability Passed: {}\n"
                + "└────────────────────────────────────────────────────────────",
                applicationId, result.get("debtToIncomeRatio"), result.get("affordabilityPassed"));
    }

    private Map<String, Object> buildResult(String applicationId) {
        return switch (applicationId) {
            case "APP-001" -> Map.of(   // Robert: £320k on £120k salary — comfortable
                    "debtToIncomeRatio",   0.28,
                    "affordabilityPassed", true);
            case "APP-002" -> Map.of(   // Marcus: tight but within tolerance; fraud is what kills this
                    "debtToIncomeRatio",   0.47,
                    "affordabilityPassed", true);
            case "APP-003" -> Map.of(   // Dmitri: £14k income vs £95k loan — clearly unaffordable
                    "debtToIncomeRatio",   0.89,
                    "affordabilityPassed", false);
            default -> Map.of(
                    "debtToIncomeRatio",   0.25,
                    "affordabilityPassed", true);
        };
    }

    private void simulateWork() {
        log.info("[{}] Running affordability model — this takes ~5 s ...", TOPIC);
        try {
            Thread.sleep(5_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
