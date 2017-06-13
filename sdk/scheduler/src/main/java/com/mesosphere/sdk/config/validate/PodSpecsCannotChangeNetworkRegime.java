package com.mesosphere.sdk.config.validate;

import com.mesosphere.sdk.dcos.DcosConstants;
import com.mesosphere.sdk.specification.NetworkSpec;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.ServiceSpec;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Validates that pods do not move from a virtual network that does not use port mapping (and thus ignores the ports
 *  as Mesos resources) to a network (virtual or Host) that does use the ports on the host machine.
 */
public class PodSpecsCannotChangeNetworkRegime implements ConfigValidator<ServiceSpec> {
    @Override
    public Collection<ConfigValidationError> validate(Optional<ServiceSpec> oldConfig, ServiceSpec newConfig) {
        if (!oldConfig.isPresent()) {
            return Collections.emptyList();
        }

        Map<String, PodSpec> newPods;
        try {
            newPods = newConfig.getPods().stream()
                    .collect(Collectors.toMap(podSpec -> podSpec.getType(), podSpec -> podSpec));
        } catch (IllegalStateException e) {
            return Arrays.asList(
                    ConfigValidationError.valueError("PodSpecs", "null", "Duplicate pod types detected."));
        }

        // check the PodSpecs to make sure none of them make a transition from a state where they use host ports
        // to one where they don't (or vice versa).
        Collection<ConfigValidationError> errors = new ArrayList<>();
        Map<String, PodSpec> oldPods = oldConfig.get().getPods().stream()
                .collect(Collectors.toMap(PodSpec::getType, podSpec -> podSpec));
        for (Map.Entry<String, PodSpec> kv: newPods.entrySet()) {
            PodSpec newPod = kv.getValue();
            // first we check that the new pod's networks (if present) do not erroneously use port mapping
            List<String> offendingNetworkNames = getNetworksWithInvalidPortMappings(newPod);
            if (offendingNetworkNames.size() > 0) {
                errors.add(ConfigValidationError.transitionError(
                        String.format("PodSpec[name:%s]", newPod.getType()),
                        "null",
                        newPod.getNetworks().toString(),
                        String.format("New config has pod %s that indicates port mapping for virtual network(s) %s, " +
                                        "that do not support port mapping.", newPod.getType(),
                                StringUtils.join(offendingNetworkNames, ", "))));
            }
            // now we check that the none of the new pods move from not using host ports to using them (or vice
            // versa)
            PodSpec oldPod = oldPods.get(kv.getKey());
            if (oldPod != null && podSpecUsesHostPorts(oldPod) != podSpecUsesHostPorts(newPod)) {
                errors.add(ConfigValidationError.transitionError(
                        String.format("PodSpec[name:%s]", oldPod.getType()),
                        oldPod.getNetworks().toString(),
                        newPod.getNetworks().toString(),
                        String.format("New config has pod %s moving networks from %s to %s, changing its " +
                                        "host ports requirements from %s to %s, not allowed.",
                                newPod.getType(), oldPod.getNetworks().toString(), newPod.getNetworks().toString(),
                                podSpecUsesHostPorts(oldPod), podSpecUsesHostPorts(newPod))));
            }
        }
        return errors;
    }

    private static boolean podSpecUsesHostPorts(PodSpec podSpec) {
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

    private static List<String> getNetworksWithInvalidPortMappings(PodSpec podSpec) {
        List<String> offendingNetworkNames = new ArrayList<>();
        for (NetworkSpec networkSpec : podSpec.getNetworks()) {
            if (!DcosConstants.networkSupportsPortMapping(networkSpec.getName())
                    && networkSpec.getPortMappings().size() > 0) {
                offendingNetworkNames.add(networkSpec.getName());
            }
        }
        return offendingNetworkNames;
    }
}
