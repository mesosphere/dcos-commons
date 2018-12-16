package com.mesosphere.sdk.hdfs.scheduler;

import com.mesosphere.sdk.scheduler.plan.DefaultPhase;
import com.mesosphere.sdk.scheduler.plan.Phase;
import com.mesosphere.sdk.scheduler.plan.Plan;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.scheduler.plan.Step;
import com.mesosphere.sdk.scheduler.plan.strategy.SerialStrategy;
import com.mesosphere.sdk.scheduler.recovery.RecoveryPlanOverrider;
import com.mesosphere.sdk.scheduler.recovery.RecoveryStep;
import com.mesosphere.sdk.scheduler.recovery.RecoveryType;
import com.mesosphere.sdk.scheduler.recovery.constrain.UnconstrainedLaunchConstrainer;
import com.mesosphere.sdk.state.StateStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

/**
 * The HdfsRecoveryPlanManager handles failure scenarios unique to HDFS.  It falls back to the default recovery behavior
 * when appropriate.
 */
public class HdfsRecoveryPlanOverrider implements RecoveryPlanOverrider {

  private static final String PHASE_NAME_TEMPLATE = "permanent-%s-failure-recovery";

  private static final String NN_PHASE_NAME = "name";

  private static final String JN_PHASE_NAME = "journal";

  private final Logger logger = LoggerFactory.getLogger(getClass());

  private final StateStore stateStore;

  private final Plan replacePlan;

  public HdfsRecoveryPlanOverrider(StateStore stateStore, Plan replacePlan) {
    this.stateStore = stateStore;
    this.replacePlan = replacePlan;
  }

  @Override
  @SuppressWarnings({
      "checkstyle:ReturnCount",
      "checkstyle:MultipleStringLiterals"
  })
  public Optional<Phase> override(PodInstanceRequirement stoppedPod) {
    if (stoppedPod.getPodInstance().getPod().getType().equals("data")
        || stoppedPod.getRecoveryType() != RecoveryType.PERMANENT)
    {
      logger.info("No overrides necessary. Pod is not a journal or name node or it isn't a " +
          "permanent failure.");
      return Optional.empty();
    }

    if (stoppedPod.getPodInstance().getPod().getType().equals(NN_PHASE_NAME)) {
      switch (stoppedPod.getPodInstance().getIndex()) {
        case 0:
          logger.info("Returning replacement plan for namenode 0.");
          return Optional.of(getRecoveryPhase(replacePlan, 0, NN_PHASE_NAME));
        case 1:
          logger.info("Returning replacement plan for namenode 1.");
          return Optional.of(getRecoveryPhase(replacePlan, 1, NN_PHASE_NAME));
        default:
          logger.error(
              "Encountered unexpected index: {}, falling back to default recovery plan manager.",
              stoppedPod.getPodInstance().getIndex());
          return Optional.empty();
      }
    } else {
      switch (stoppedPod.getPodInstance().getIndex()) {
        case 0:
          logger.info("Returning replacement plan for journalnode 0.");
          return Optional.of(getRecoveryPhase(replacePlan, 0, JN_PHASE_NAME));
        case 1:
          logger.info("Returning replacement plan for journalnode 1.");
          return Optional.of(getRecoveryPhase(replacePlan, 1, JN_PHASE_NAME));
        case 2:
          logger.info("Returning replacement plan for journalnode 2.");
          return Optional.of(getRecoveryPhase(replacePlan, 2, JN_PHASE_NAME));
        default:
          logger.error(
              "Encountered unexpected index: {}, falling back to default recovery plan manager.",
              stoppedPod.getPodInstance().getIndex());
          return Optional.empty();
      }

    }
  }

  private Phase getRecoveryPhase(Plan inputPlan, int index, String phaseName) {
    Phase inputPhase = getPhaseForNodeType(inputPlan, phaseName);
    int offset = index * 2;

    // Bootstrap
    Step inputBootstrapStep = inputPhase.getChildren().get(offset);
    PodInstanceRequirement bootstrapPodInstanceRequirement =
        PodInstanceRequirement.newBuilder(
            inputBootstrapStep.getPodInstanceRequirement().get().getPodInstance(),
            inputBootstrapStep.getPodInstanceRequirement().get().getTasksToLaunch())
            .recoveryType(RecoveryType.PERMANENT)
            .build();
    Step bootstrapStep =
        new RecoveryStep(
            inputBootstrapStep.getName(),
            bootstrapPodInstanceRequirement,
            new UnconstrainedLaunchConstrainer(),
            stateStore);

    // JournalNode or NameNode
    Step inputNodeStep = inputPhase.getChildren().get(offset + 1);
    PodInstanceRequirement nameNodePodInstanceRequirement =
        PodInstanceRequirement.newBuilder(
            inputNodeStep.getPodInstanceRequirement().get().getPodInstance(),
            inputNodeStep.getPodInstanceRequirement().get().getTasksToLaunch())
            .recoveryType(RecoveryType.TRANSIENT)
            .build();
    Step nodeStep =
        new RecoveryStep(
            inputNodeStep.getName(),
            nameNodePodInstanceRequirement,
            new UnconstrainedLaunchConstrainer(),
            stateStore);

    return new DefaultPhase(
        String.format(PHASE_NAME_TEMPLATE, phaseName),
        Arrays.asList(bootstrapStep, nodeStep),
        new SerialStrategy<>(),
        Collections.emptyList());
  }

  private static Phase getPhaseForNodeType(Plan inputPlan, String phaseName) {
    Optional<Phase> phaseOptional = inputPlan.getChildren().stream()
        .filter(phase -> phase.getName().equals(phaseName))
        .findFirst();

    if (!phaseOptional.isPresent()) {
      throw new RuntimeException(
          String.format("Expected phase name %s does not exist in the service spec plan", phaseName)
      );
    }

    return phaseOptional.get();
  }
}
