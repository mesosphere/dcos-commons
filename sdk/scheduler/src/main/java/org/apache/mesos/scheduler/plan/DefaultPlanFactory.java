package org.apache.mesos.scheduler.plan;

import org.apache.mesos.config.ConfigStore;
import org.apache.mesos.offer.OfferRequirementProvider;
import org.apache.mesos.scheduler.plan.strategy.SerialStrategy;
import org.apache.mesos.scheduler.plan.strategy.Strategy;
import org.apache.mesos.scheduler.plan.strategy.StrategyGenerator;
import org.apache.mesos.specification.ServiceSpecification;
import org.apache.mesos.state.StateStore;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Given a StateStore and a PlanSpecification the DefaultPlanFactory can generate a Plan.
 */
public class DefaultPlanFactory implements PlanFactory {
    private final StrategyGenerator<Phase> strategyGenerator;
    private final PhaseFactory phaseFactory;

    public DefaultPlanFactory(
            ConfigStore configStore,
            StateStore stateStore,
            OfferRequirementProvider offerRequirementProvider,
            StrategyGenerator<Phase> strategyGenerator) {
        this(
                new DefaultPhaseFactory(
                        new DefaultStepFactory(configStore, stateStore, offerRequirementProvider)),
                strategyGenerator);
    }

    public DefaultPlanFactory(PhaseFactory phaseFactory) {
        this(phaseFactory, new SerialStrategy.Generator<>());
    }

    public DefaultPlanFactory(PhaseFactory phaseFactory, StrategyGenerator<Phase> strategyGenerator) {
        this.phaseFactory = phaseFactory;
        this.strategyGenerator = strategyGenerator;
    }

    public static Plan getPlan(String name, List<Phase> phases, Strategy<Phase> strategy) {
        return getPlan(name, phases, strategy, Collections.emptyList());
    }

    public static Plan getPlan(String name, List<Phase> phases, Strategy<Phase> strategy, List<String> errors) {
        return new DefaultPlan(name, phases, strategy, errors);
    }

    @Override
    public Plan getPlan(ServiceSpecification serviceSpecification) {
        return new DefaultPlan(
                serviceSpecification.getName(),
                getPhases(serviceSpecification),
                strategyGenerator.generate());
    }

    private List<Phase> getPhases(ServiceSpecification serviceSpecification) {
        return serviceSpecification.getTaskSets().stream()
                .map(phaseFactory::getPhase)
                .collect(Collectors.toList());
    }
}
