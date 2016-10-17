package org.apache.mesos.scheduler.plan;

import org.apache.mesos.scheduler.plan.strategy.SerialStrategy;
import org.apache.mesos.scheduler.plan.strategy.Strategy;
import org.apache.mesos.specification.ServiceSpecification;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Given a StateStore and a PlanSpecification the DefaultPlanFactory can generate a Plan.
 */
public class DefaultPlanFactory implements PlanFactory {
    private final Strategy<? extends Phase> strategy;
    private final PhaseFactory phaseFactory;

    public DefaultPlanFactory(PhaseFactory phaseFactory) {
        this(phaseFactory, new SerialStrategy());
    }

    public DefaultPlanFactory(PhaseFactory phaseFactory, Strategy<? extends Phase> strategy) {
        this.strategy = strategy;
        this.phaseFactory = phaseFactory;
    }

    @Override
    public Plan getPlan(ServiceSpecification serviceSpecification) {
        return new Default(
                serviceSpecification.getName(),
                strategy,
                getPhases(serviceSpecification),
                Collections.emptyList());
    }

    private List<Element> getPhases(ServiceSpecification serviceSpecification) {
        return serviceSpecification.getTaskSets().stream()
                .map(phaseFactory::getPhase)
                .collect(Collectors.toList());
    }
}
