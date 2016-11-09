package org.apache.mesos.scheduler.plan;

import org.apache.mesos.scheduler.plan.strategy.SerialStrategy;
import org.apache.mesos.scheduler.plan.strategy.Strategy;
import org.apache.mesos.scheduler.plan.strategy.StrategyGenerator;
import org.apache.mesos.specification.ServiceSpec;

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

    public static Plan getPlan(String name, List<Phase> phases, Strategy<Phase> strategy) {
        return getPlan(name, phases, strategy, Collections.emptyList());
    }

    public static Plan getPlan(String name, List<Phase> phases, Strategy<Phase> strategy, List<String> errors) {
        return new DefaultPlan(name, phases, strategy, errors);
    }

    @Override
    public Plan getPlan(ServiceSpec serviceSpec) {
        return new DefaultPlan(
                serviceSpec.getName(),
                getPhases(serviceSpec),
                strategyGenerator.generate());
    }

    private List<Phase> getPhases(ServiceSpec serviceSpec) {
        return serviceSpec.getPods().stream()
                .map(phaseFactory::getPhase)
                .collect(Collectors.toList());
    }
}
