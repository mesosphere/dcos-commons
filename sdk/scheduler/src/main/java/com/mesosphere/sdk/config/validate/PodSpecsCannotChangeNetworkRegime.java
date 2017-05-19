package com.mesosphere.sdk.config.validate;

import com.mesosphere.sdk.dcos.DcosConstants;
import com.mesosphere.sdk.specification.NetworkSpec;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.ServiceSpec;
import javafx.util.Pair;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Validates that pods do not move from a virtual network that does not use port mapping (and thus ignores the ports
 *  as Mesos resources) to a network (virtual or Host) that does use the ports on the host machine.
 */
public class PodSpecsCannotChangeNetworkRegime implements ConfigValidator<ServiceSpec> {

    @Override
    public Collection<ConfigValidationError> validate(ServiceSpec nullableOldConfig, ServiceSpec newConfig) {
        Pair<List<ConfigValidationError>, Map<String, PodSpec>> pair = validateInitialConfigs(
                nullableOldConfig, newConfig);
        List<ConfigValidationError> errors = pair.getKey();
        Map<String, PodSpec> newPods = pair.getValue();
        if (newPods.isEmpty()) return errors;

        // check the PodSpecs to make sure none of them make a transition form a state where they use host ports
        // to one where they don't (or vice versa).
        for (PodSpec oldPod : nullableOldConfig.getPods()) {
            PodSpec newPod = newPods.get(oldPod.getType());
            if (podSpecUsesHostPorts(oldPod) != podSpecUsesHostPorts(newPod)) {
                errors.add(ConfigValidationError.transitionError(
                        String.format("PodSpec[name:%s]", oldPod.getType()),
                        String.format("%s", oldPod.getNetworks().toString()),
                        String.format("%s", newPod.getNetworks().toString()),
                        String.format("New config has pod %s moving networks from %s to %s, changing it's " +
                                "host ports requirements from %s to %s, not allowed.",
                                newPod.getType(), oldPod.getNetworks().toString(), newPod.getNetworks().toString(),
                                podSpecUsesHostPorts(oldPod), podSpecUsesHostPorts(newPod))
                ));
            }
        }
        return errors;
    }

    private boolean podSpecUsesHostPorts(PodSpec podSpec) {
        if (podSpec.getNetworks().size() == 0) return true;
        for (NetworkSpec networkSpec : podSpec.getNetworks()) {
            if (DcosConstants.networkSupportsPortMapping(networkSpec.getName())) return true;
        }
        return false;
    }
}
