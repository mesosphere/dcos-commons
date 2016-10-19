package org.apache.mesos.scheduler.plan;

import org.apache.mesos.scheduler.plan.strategy.SerialStrategy;
import org.apache.mesos.scheduler.plan.strategy.StrategyGenerator;
import org.apache.mesos.specification.ServiceSpecification;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Given a StateStore and a PlanSpecification the DefaultPlanFactory can generate a Plan.
 */
public class DefaultPlanFactory implements PlanFactory {
    private final StrategyGenerator<Phase> strategyGenerator;
    private final PhaseFactory phaseFactory;

    public DefaultPlanFactory(PhaseFactory phaseFactory) {
        this(phaseFactory, new SerialStrategy.Generator<>());
    }

    public DefaultPlanFactory(PhaseFactory phaseFactory, StrategyGenerator<Phase> strategyGenerator) {
        this.phaseFactory = phaseFactory;
        this.strategyGenerator = strategyGenerator;
    }

    @Override
    public Plan getPlan(ServiceSpecification serviceSpecification) {
        return new DefaultPlan(
                serviceSpecification.getName(),
                getPhases(serviceSpecification),
                strategyGenerator.generate(),
                Collections.emptyList());
    }

    private List<Element> getPhases(ServiceSpecification serviceSpecification) {
        return serviceSpecification.getTaskSets().stream()
                .map(phaseFactory::getPhase)
                .collect(Collectors.toList());
    }
}
