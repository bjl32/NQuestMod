package cn.zbx1425.nquestmod.data.quest;

import java.util.*;

public class QuestProgress {

    public String questId;
    public int currentStepIndex;
    public long questStartTime;

    public long currentStepSessionStartTime;
    public Map<Integer, Long> previousSessionsStepDurationsMillis;
    public Map<Integer, List<String>> stepLinesRidden;

    public Quest questSnapshot;

    public StepState criteriaState;
    public StepState failureCriteriaState;
    public StepState defaultFailureCriteriaState;

    public transient Step expandedCurrentStep;
    public transient Step expandedDefaultCriteria;

    public void resetStepStates() {
        this.criteriaState = new StepState();
        this.failureCriteriaState = new StepState();
        this.defaultFailureCriteriaState = new StepState();
        this.expandedCurrentStep = null;
        this.expandedDefaultCriteria = null;
    }

    public void ensureInitialized() {
        if (previousSessionsStepDurationsMillis == null) previousSessionsStepDurationsMillis = new HashMap<>();
        if (stepLinesRidden == null) stepLinesRidden = new HashMap<>();
    }

    /** Pause the current step timer, adding elapsed time to accumulated millis. */
    public void pauseCurrentStep() {
        ensureInitialized();
        long now = System.currentTimeMillis();
        previousSessionsStepDurationsMillis.merge(currentStepIndex, now - currentStepSessionStartTime, Long::sum);
        currentStepSessionStartTime = now;
    }

    /** Resume the current step timer by resetting its start time to now. */
    public void resumeCurrentStep() {
        currentStepSessionStartTime = System.currentTimeMillis();
    }

    /** Get the effective duration for the current step, accounting for accumulated offline-adjusted time. */
    public long getCurrentStepDuration(long endTimestamp) {
        ensureInitialized();
        long accumulated = previousSessionsStepDurationsMillis.getOrDefault(currentStepIndex, 0L);
        return accumulated + (endTimestamp - currentStepSessionStartTime);
    }

    /** Finalize the current step's duration into previousSessionsStepDurationsMillis. */
    public void finalizeCurrentStep(long endTimestamp) {
        ensureInitialized();
        previousSessionsStepDurationsMillis.put(currentStepIndex, getCurrentStepDuration(endTimestamp));
    }
}
