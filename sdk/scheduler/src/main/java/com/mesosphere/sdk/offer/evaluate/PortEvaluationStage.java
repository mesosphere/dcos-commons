package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.dcos.DcosConstants;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.offer.MesosResourcePool;
import com.mesosphere.sdk.offer.ResourceBuilder;
import com.mesosphere.sdk.offer.TaskException;
import com.mesosphere.sdk.offer.taskdata.EnvUtils;
import com.mesosphere.sdk.offer.taskdata.TaskLabelReader;
import com.mesosphere.sdk.offer.taskdata.TaskLabelWriter;
import com.mesosphere.sdk.specification.PortSpec;
import com.mesosphere.sdk.specification.ResourceSpec;
import com.mesosphere.sdk.specification.TaskSpec;

import com.google.protobuf.TextFormat;
import org.apache.mesos.Protos;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


/**
 * This class evaluates an offer for a single port against a
 * {@link com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement}, finding a port dynamically in the offer where
 * specified by the framework and modifying {@link org.apache.mesos.Protos.TaskInfo} and
 * {@link org.apache.mesos.Protos.ExecutorInfo} where appropriate so that the port is available in their respective
 * environments.
 */
public class PortEvaluationStage implements OfferEvaluationStage {

  private final Logger logger;

  private final PortSpec portSpec;

  private final Collection<String> taskNames;

  private final Optional<String> resourceId;

  private final Optional<String> resourceNamespace;

  private final boolean useHostPorts;

  public PortEvaluationStage(
      PortSpec portSpec,
      Collection<String> taskNames,
      Optional<String> resourceId,
      Optional<String> resourceNamespace)
  {
    this.logger = LoggingUtils.getLogger(getClass(), resourceNamespace);
    this.portSpec = portSpec;
    this.taskNames = taskNames;
    this.resourceId = resourceId;
    this.resourceNamespace = resourceNamespace;
    this.useHostPorts = requireHostPorts(portSpec.getNetworkNames());
  }

  @Override
  public EvaluationOutcome evaluate(MesosResourcePool mesosResourcePool,
                                    PodInfoBuilder podInfoBuilder)
  {
    long requestedPort = portSpec.getValue().getRanges().getRange(0).getBegin();
    long assignedPort = requestedPort;
    if (requestedPort == 0) {
      // If this is from an existing pod with the dynamic port already assigned and reserved, just keep it.
      Optional<Long> priorTaskPort = getTaskNames().stream()
          .map(taskName -> podInfoBuilder.getPriorPortForTask(taskName, portSpec))
          .filter(priorPortForTask -> priorPortForTask.isPresent())
          .map(priorPortForTask -> priorPortForTask.get())
          .findAny();
      if (priorTaskPort.isPresent()) {
        // Reuse the prior port value.
        assignedPort = priorTaskPort.get();
        logger.info("Using previously reserved dynamic port: {}", assignedPort);
      } else {
        // Choose a new port value.
        String preReservedRole = podInfoBuilder.getPodInstance().getPod().getPreReservedRole();
        Optional<Integer> dynamicPort = useHostPorts ?
            selectDynamicPort(mesosResourcePool, podInfoBuilder, preReservedRole) :
            selectOverlayPort(podInfoBuilder);
        if (!dynamicPort.isPresent()) {
          return EvaluationOutcome.fail(
              this,
              "No ports were available for dynamic claim in offer," +
                  " and no matching port %s was present in prior %s: %s %s",
              portSpec.getPortName(),
              getTaskNames().isEmpty() ? "executor" : "tasks: " + getTaskNames(),
              TextFormat.shortDebugString(mesosResourcePool.getOffer()),
              podInfoBuilder.toString())
              .build();
        }
        assignedPort = dynamicPort.get();
        logger.info("Claiming new dynamic port: {}", assignedPort);
      }
    }

    // Update portSpec to reflect the assigned port value (for example, to reflect a dynamic port allocation):
    Protos.Value.Builder valueBuilder = Protos.Value.newBuilder()
        .setType(Protos.Value.Type.RANGES);
    valueBuilder.getRangesBuilder().addRangeBuilder()
        .setBegin(assignedPort)
        .setEnd(assignedPort);
    PortSpec updatedPortSpec = PortSpec.withValue(portSpec, valueBuilder.build());

    if (useHostPorts) {
      OfferEvaluationUtils.ReserveEvaluationOutcome reserveEvaluationOutcome =
          OfferEvaluationUtils.evaluateSimpleResource(
              logger,
              this,
              updatedPortSpec,
              resourceId,
              resourceNamespace,
              mesosResourcePool);
      EvaluationOutcome evaluationOutcome = reserveEvaluationOutcome.getEvaluationOutcome();
      if (!evaluationOutcome.isPassing()) {
        return evaluationOutcome;
      }

      Optional<String> resourceIdResult = reserveEvaluationOutcome.getResourceId();
      setProtos(podInfoBuilder,
          ResourceBuilder.fromSpec(updatedPortSpec, resourceIdResult, resourceNamespace).build());
      return EvaluationOutcome.pass(
          this,
          evaluationOutcome.getOfferRecommendations(),
          "Offer contains required %sport: '%s' with resourceId: '%s'",
          resourceId.isPresent() ? "previously reserved " : "",
          assignedPort,
          resourceId)
          .mesosResource(evaluationOutcome.getMesosResource().get())
          .build();
    } else {
      setProtos(podInfoBuilder,
          ResourceBuilder.fromSpec(updatedPortSpec, resourceId, resourceNamespace).build());
      return EvaluationOutcome.pass(
          this,
          "Port %s doesn't require resource reservation, " +
              "ignoring resource requirements and using port %d",
          portSpec.getPortName(), assignedPort)
          .build();
    }
  }

  /**
   * Overridden in VIP evaluation.
   */
  protected void setProtos(PodInfoBuilder podInfoBuilder, Protos.Resource resource) {
    long port = resource.getRanges().getRange(0).getBegin();

    final String portEnvKey = portSpec.getEnvKey();
    final String portEnvVal = Long.toString(port);

    // Add info to each task:
    for (String taskName : taskNames) {
      Protos.TaskInfo.Builder taskBuilder = podInfoBuilder.getTaskBuilder(taskName);

      if (!taskBuilder.hasDiscovery()) {
        // Initialize with defaults:
        taskBuilder.getDiscoveryBuilder()
            .setVisibility(Constants.DEFAULT_TASK_DISCOVERY_VISIBILITY)
            .setName(taskBuilder.getName());
      }
      taskBuilder.getDiscoveryBuilder().getPortsBuilder().addPortsBuilder()
          .setNumber((int) port)
          .setVisibility(portSpec.getVisibility())
          .setProtocol(DcosConstants.DEFAULT_IP_PROTOCOL)
          .setName(portSpec.getPortName());

      if (portEnvKey != null) {

        // Add port to the main task environment:
        taskBuilder.getCommandBuilder().setEnvironment(
            EnvUtils.withEnvVar(
                taskBuilder.getCommandBuilder().getEnvironment(),
                portEnvKey,
                portEnvVal));

        // Add port to the health check environment (if defined):
        if (taskBuilder.hasHealthCheck()) {
          Protos.CommandInfo.Builder healthCheckCmdBuilder =
              taskBuilder.getHealthCheckBuilder().getCommandBuilder();
          healthCheckCmdBuilder.setEnvironment(
              EnvUtils.withEnvVar(healthCheckCmdBuilder.getEnvironment(), portEnvKey, portEnvVal));
        } else {
          logger.info("Health check is not defined for task: {}", taskName);
        }

        // Add port to the readiness check environment (if a readiness check is defined):
        if (taskBuilder.hasCheck()) {
          // Readiness check version used with default executor
          Protos.CommandInfo.Builder checkCmdBuilder =
              taskBuilder.getCheckBuilder().getCommandBuilder().getCommandBuilder();
          checkCmdBuilder.setEnvironment(
              EnvUtils.withEnvVar(checkCmdBuilder.getEnvironment(), portEnvKey, portEnvVal));
        }
        if (new TaskLabelReader(taskBuilder).hasReadinessCheckLabel()) {
          // Readiness check version used with custom executor
          try {
            taskBuilder.setLabels(new TaskLabelWriter(taskBuilder)
                .setReadinessCheckEnvvar(portEnvKey, portEnvVal)
                .toProto());
          } catch (TaskException e) {
            logger.error("Got exception while adding PORT env var to ReadinessCheck", e);
          }
        }
      }

      if (useHostPorts) {
        // we only use the resource if we're using the host ports
        taskBuilder.addResources(resource);
      }
    }

    // No tasks specified, maybe it's for the executor? In practice this probably doesn't happen:
    if (getTaskNames().isEmpty()) {
      Protos.ExecutorInfo.Builder executorBuilder = podInfoBuilder.getExecutorBuilder().get();
      if (portEnvKey != null) {
        Protos.CommandInfo.Builder executorCmdBuilder = executorBuilder.getCommandBuilder();
        executorCmdBuilder.setEnvironment(
            EnvUtils.withEnvVar(executorCmdBuilder.getEnvironment(), portEnvKey, portEnvVal));
      }
      if (useHostPorts) {
        executorBuilder.addResources(resource);
      }

    }
  }

  private static Optional<Integer> selectDynamicPort(
      MesosResourcePool mesosResourcePool, PodInfoBuilder podInfoBuilder, String preReservedRole)
  {
    Set<Integer> consumedPorts = new HashSet<>();

    // We don't want to accidentally dynamically consume a port that's explicitly claimed elsewhere in this pod, so
    // compile a list of those to check against the offered ports.
    for (TaskSpec task : podInfoBuilder.getPodInstance().getPod().getTasks()) {
      for (ResourceSpec resourceSpec : task.getResourceSet().getResources()) {
        if (resourceSpec instanceof PortSpec) {
          PortSpec portSpec = (PortSpec) resourceSpec;
          if (portSpec.getPort() != 0) {
            consumedPorts.add((int) portSpec.getPort());
          }
        }
      }
    }

    // Also check other dynamically allocated ports which had been taken by earlier stages of this evaluation round.
    for (Protos.Resource.Builder resourceBuilder : podInfoBuilder.getTaskResourceBuilders()) {
      consumedPorts.addAll(getPortsInResource(resourceBuilder.build()));
    }
    for (Protos.Resource.Builder resourceBuilder : podInfoBuilder.getExecutorResourceBuilders()) {
      consumedPorts.addAll(getPortsInResource(resourceBuilder.build()));
    }

    Protos.Value availablePorts = mesosResourcePool
            .getUnreservedMergedPoolByRole(preReservedRole)
            .get(Constants.PORTS_RESOURCE_TYPE);
    Optional<Integer> dynamicPort = Optional.empty();
    if (availablePorts != null) {
      dynamicPort = availablePorts.getRanges().getRangeList().stream()
          .flatMap(r -> IntStream.rangeClosed((int) r.getBegin(), (int) r.getEnd()).boxed())
          .filter(p -> !consumedPorts.contains(p))
          .findFirst();
    }

    return dynamicPort;
  }

  private static Optional<Integer> selectOverlayPort(PodInfoBuilder podInfoBuilder) {
    // take the next available port in the range.
    Optional<Integer> dynamicPort = Optional.empty();
    for (Integer i = DcosConstants.OVERLAY_DYNAMIC_PORT_RANGE_START;
         i <= DcosConstants.OVERLAY_DYNAMIC_PORT_RANGE_END; i++)
    {
      if (!podInfoBuilder.isAssignedOverlayPort(i.longValue())) {
        dynamicPort = Optional.of(i);
        podInfoBuilder.addAssignedOverlayPort(i.longValue());
        break;
      }
    }
    return dynamicPort;
  }

  private static Set<Integer> getPortsInResource(Protos.Resource resource) {
    if (!resource.getName().equals(Constants.PORTS_RESOURCE_TYPE)) {
      return Collections.emptySet();
    }
    return resource.getRanges().getRangeList().stream()
        .flatMap(r -> IntStream.rangeClosed((int) r.getBegin(), (int) r.getEnd()).boxed())
        .filter(p -> p != 0)
        .collect(Collectors.toSet());
  }

  private static boolean requireHostPorts(Collection<String> networkNames) {
    if (networkNames.isEmpty()) {
      // no network names, must be on host network and use the host IP
      return true;
    } else {
      return networkNames.stream()
          .filter(DcosConstants::networkSupportsPortMapping)
          .count() > 0;
    }
  }

  protected Collection<String> getTaskNames() {
    return taskNames;
  }
}
