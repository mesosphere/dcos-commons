package com.mesosphere.sdk.config.validate;

import com.google.common.base.Strings;
import com.mesosphere.sdk.dcos.DcosConstants;
import com.mesosphere.sdk.specification.NetworkSpec;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.ServiceSpec;
import javafx.util.Pair;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
        if (newPods.isEmpty()) {
            return errors;
        }

        // check the PodSpecs to make sure none of them make a transition form a state where they use host ports
        // to one where they don't (or vice versa).

        Map<String, PodSpec> oldPods = nullableOldConfig.getPods().stream()
                .collect(Collectors.toMap(PodSpec::getType, podSpec -> podSpec));
        for (Map.Entry<String, PodSpec> kv: newPods.entrySet()) {
            PodSpec newPod = kv.getValue();
            // first we check that the new pod's networks (if present) do not erroneously use port mapping
            String offendingNetworkNames = podSpecOverlayNetworkIsCorrect(newPod);
            if (!Strings.isNullOrEmpty(offendingNetworkNames)) {
                errors.add(ConfigValidationError.transitionError(
                        String.format("PodSpec[name:%s]", newPod.getType()),
                        "null",
                        String.format("%s", newPod.getNetworks().toString()),
                        String.format("New config has pod %s that uses network(s) %s, port mapping is " +
                                        "not supported on these networks. NetworkSpec: %s", newPod.getType(),
                                offendingNetworkNames, newPod.getNetworks().toString())));
            }
            // now we check that the none of the new pods move from not using host ports to using them (or vice
            // versa)
            PodSpec oldPod = oldPods.get(kv.getKey());
            if (oldPod != null && podSpecUsesHostPorts(oldPod) != podSpecUsesHostPorts(newPod)) {
                errors.add(ConfigValidationError.transitionError(
                        String.format("PodSpec[name:%s]", oldPod.getType()),
                        String.format("%s", oldPod.getNetworks().toString()),
                        String.format("%s", newPod.getNetworks().toString()),
                        String.format("New config has pod %s moving networks from %s to %s, changing it's " +
                                        "host ports requirements from %s to %s, not allowed.",
                                newPod.getType(), oldPod.getNetworks().toString(), newPod.getNetworks().toString(),
                                podSpecUsesHostPorts(oldPod), podSpecUsesHostPorts(newPod))));
            }
        }
        return errors;
    }

    private boolean podSpecUsesHostPorts(PodSpec podSpec) {
        if (podSpec.getNetworks().size() == 0) {  // using HOST network, uses ports
            return true;
        }
        for (NetworkSpec networkSpec : podSpec.getNetworks()) {
            if (DcosConstants.networkSupportsPortMapping(networkSpec.getName())) {
                return true;
            }
        }
        return false;
    }

    private String podSpecOverlayNetworkIsCorrect(PodSpec podSpec) {
        StringBuilder stringBuilder = new StringBuilder();
        for (NetworkSpec networkSpec : podSpec.getNetworks()) {
            if (!DcosConstants.networkSupportsPortMapping(networkSpec.getName())
                    && networkSpec.getPortMappings().size() > 0) {
                stringBuilder.append(String.format("%s, ", networkSpec.getName()));
            }
        }
        return stringBuilder.toString();
    }
}
