package com.mesosphere.sdk.scheduler.plan;

import com.mesosphere.sdk.offer.CommonIdUtils;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.scheduler.plan.backoff.Delay;
import com.mesosphere.sdk.scheduler.plan.backoff.ExponentialBackOff;
import com.mesosphere.sdk.scheduler.plan.strategy.SerialStrategy;
import com.mesosphere.sdk.scheduler.plan.strategy.Strategy;
import com.mesosphere.sdk.scheduler.plan.strategy.StrategyGenerator;
import com.mesosphere.sdk.specification.ServiceSpec;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Given a PhaseFactory and a StrategyGenerator for the Phases, the DeployPlanFactory generates a Plan.
 */
public class DeployPlanFactory implements PlanFactory {
  private final StrategyGenerator<Phase> strategyGenerator;

  private final PhaseFactory phaseFactory;

  public DeployPlanFactory(PhaseFactory phaseFactory) {
    this(phaseFactory, new SerialStrategy.Generator<>());
  }

  public DeployPlanFactory(PhaseFactory phaseFactory, StrategyGenerator<Phase> strategyGenerator) {
    this.phaseFactory = phaseFactory;
    this.strategyGenerator = strategyGenerator;
  }

  public static Plan getPlan(String name, List<Phase> phases, Strategy<Phase> strategy) {
    return getPlan(name, phases, strategy, Collections.emptyList());
  }

  public static Plan getPlan(
      String name,
      List<Phase> phases,
      Strategy<Phase> strategy,
      List<String> errors)
  {
    return new DeploymentPlan(name, phases, strategy, errors);
  }

  @Override
  public Plan getPlan(ServiceSpec serviceSpec) {
    List<Phase> phases = serviceSpec.getPods().stream()
        .map(phaseFactory::getPhase)
        .collect(Collectors.toList());
    return new DeploymentPlan(
            Constants.DEPLOY_PLAN_NAME, phases, strategyGenerator.generate(phases));
  }

  private static class DeploymentPlan extends DefaultPlan {

    DeploymentPlan(String name, List<Phase> phases, Strategy<Phase> strategy) {
      super(name, phases, strategy);
    }

    DeploymentPlan(String name, List<Phase> phases, Strategy<Phase> strategy, List<String> errors) {
      super(name, phases, strategy, errors);
    }

    @Override
    public Collection<? extends Step> getCandidates(
            Collection<PodInstanceRequirement> dirtyAssets)
    {
      Collection<? extends Step> candidates = super.getCandidates(dirtyAssets);
      LOGGER.info("DeployPlanFactory -------->");
      return filterBackedOffTasks(candidates);
    }

    private Collection<? extends Step> filterBackedOffTasks(
            Collection<? extends Step> candidates)
    {
      ExponentialBackOff exponentialBackOff = ExponentialBackOff.getInstance();
      LOGGER.info("before {}", candidates);
      List<? extends Step> filteredCandidates = candidates.stream()
          .filter(DeploymentStep.class::isInstance)
          .map(step -> {
            DeploymentStep deploymentStep = (DeploymentStep) step;
            List<String> filteredTaskNames = deploymentStep
                .podInstanceRequirement
                .getTasksToLaunch()
                .stream()
                .filter(taskId -> {
                  String task = CommonIdUtils.getTaskInstanceName(
                          deploymentStep.podInstanceRequirement.getPodInstance(), taskId);
                  Delay delay = exponentialBackOff.getDelay(task);
                  if (delay == null) {
                    LOGGER.info("no delay found for {}", task);
                    return true;
                  } else if (delay.isOver()) {
                    LOGGER.info("Delay elapsed for {}", task);
                    return true;
                  }
                  LOGGER.info("Current delay for {} is {}",
                          task, delay.getCurrentDelay().toString());
                  return false;
                })
                .collect(Collectors.toList());
            LOGGER.info("filtered tasks {}", filteredTaskNames);
            LOGGER.info("old tasks {}", deploymentStep.podInstanceRequirement.getTasksToLaunch());
            deploymentStep.podInstanceRequirement.filterTasksToLaunch(filteredTaskNames);
            LOGGER.info("new tasks {}", deploymentStep.podInstanceRequirement.getTasksToLaunch());
            return deploymentStep;
          })
          .filter(step -> !step.podInstanceRequirement.getTasksToLaunch().isEmpty())
          .collect(Collectors.toList());
      LOGGER.info("after {}", filteredCandidates);
      return filteredCandidates;
    }
  }
}
