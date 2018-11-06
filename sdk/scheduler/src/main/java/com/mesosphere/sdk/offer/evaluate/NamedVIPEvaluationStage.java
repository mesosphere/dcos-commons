package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.taskdata.AuxLabelAccess;
import com.mesosphere.sdk.specification.NamedVIPSpec;

import org.apache.mesos.Protos;

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * This class evaluates an offer against a given {@link com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement}
 * for port resources as in {@link PortEvaluationStage}, additionally setting
 * {@link org.apache.mesos.Protos.DiscoveryInfo} properly for DC/OS to pick up the specified named VIP mapping.
 */
public class NamedVIPEvaluationStage extends PortEvaluationStage {

  private final NamedVIPSpec namedVIPSpec;

  public NamedVIPEvaluationStage(
      NamedVIPSpec namedVIPSpec,
      Collection<String> taskNames,
      Optional<String> resourceId,
      Optional<String> resourceNamespace)
  {
    super(namedVIPSpec, taskNames, resourceId, resourceNamespace);
    this.namedVIPSpec = namedVIPSpec;
  }

  @Override
  protected void setProtos(PodInfoBuilder podInfoBuilder, Protos.Resource resource) {
    super.setProtos(podInfoBuilder, resource);

    // Find the matching port entry which was created above.
    Optional<Protos.Port.Builder> portBuilder = getTaskNames().stream()
        .map(taskName -> podInfoBuilder.getTaskBuilder(taskName)
            .getDiscoveryBuilder().getPortsBuilder().getPortsBuilderList().stream()
            .filter(port -> port.getName().equals(namedVIPSpec.getPortName()))
            .collect(Collectors.toList()))
        .filter(portBuilders -> portBuilders.size() == 1)
        .map(portBuilders -> portBuilders.get(0))
        .findAny();
    if (!portBuilder.isPresent()) {
      throw new IllegalStateException(String.format(
          "Unable to find port entry with name %s in tasks: %s",
          namedVIPSpec.getPortName(), getTaskNames()));
    }

    // Update port entry with VIP metadata.
    portBuilder.get().setProtocol(namedVIPSpec.getProtocol());
    AuxLabelAccess.setVIPLabels(portBuilder.get(), namedVIPSpec);
  }
}
