package com.mesosphere.sdk.scheduler.plan;

import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.specification.yaml.RawPlan;

import java.util.Collection;

/**
 * PlanGenerator generates Plans from a given {@link ServiceSpec}.
 */
public interface PlanGenerator {
    Plan generate(RawPlan rawPlan, String planName, Collection<PodSpec> podsSpecs);
}
