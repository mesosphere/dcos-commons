package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.specification.DefaultResourceSpec;
import com.mesosphere.sdk.specification.NamedVIPSpec;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.PortSpec;
import com.mesosphere.sdk.specification.ResourceSpec;
import com.mesosphere.sdk.specification.TaskSpec;
import com.mesosphere.sdk.specification.VolumeSpec;
import org.apache.mesos.Protos;

import java.util.ArrayList;
import java.util.List;

/**
 * The DefaultExecutorVisitor traverses a {@link PodSpec} and initiates visits to the implicit resources required for
 * the default executor.
 */
public class DefaultExecutorVisitor extends NullVisitor<EvaluationOutcome> {

    public DefaultExecutorVisitor(SpecVisitor delegate) {
        super(delegate);
    }

    @Override
    public PodInstanceRequirement visitImplementation(
            PodInstanceRequirement podInstanceRequirement) {
        return podInstanceRequirement;
    }

    @Override
    public PodSpec visit(PodSpec podSpec) throws SpecVisitorException {
        PodSpec visited = super.visit(podSpec);

        List<ResourceSpec> executorResources = getExecutorResources(
                visited.getPreReservedRole(), getPodRole(visited), getPodPrincipal(visited));

        for (ResourceSpec resourceSpec : executorResources) {
            visit(resourceSpec);
        }

        return visited;
    }

    @Override
    public PodSpec visitImplementation(PodSpec podSpec) {
        return podSpec;
    }

    @Override
    public TaskSpec visitImplementation(TaskSpec taskSpec) {
        return taskSpec;
    }

    @Override
    public ResourceSpec visitImplementation(ResourceSpec resourceSpec) {
        return resourceSpec;
    }

    @Override
    public VolumeSpec visitImplementation(VolumeSpec volumeSpec) {
        return volumeSpec;
    }

    @Override
    public PortSpec visitImplementation(PortSpec portSpec) {
        return portSpec;
    }

    @Override
    public NamedVIPSpec visitImplementation(NamedVIPSpec namedVIPSpec) throws SpecVisitorException {
        return namedVIPSpec;
    }

    private static List<ResourceSpec> getExecutorResources(String preReservedRole, String role, String principal) {
        List<ResourceSpec> resources = new ArrayList<>();

        resources.add(DefaultResourceSpec.newBuilder()
                .name("cpus")
                .preReservedRole(preReservedRole)
                .role(role)
                .principal(principal)
                .value(Protos.Value.newBuilder()
                        .setType(Protos.Value.Type.SCALAR)
                        .setScalar(Protos.Value.Scalar.newBuilder().setValue(0.1))
                        .build())
                .build());

        resources.add(DefaultResourceSpec.newBuilder()
                .name("mem")
                .preReservedRole(preReservedRole)
                .role(role)
                .principal(principal)
                .value(Protos.Value.newBuilder()
                        .setType(Protos.Value.Type.SCALAR)
                        .setScalar(Protos.Value.Scalar.newBuilder().setValue(32.0))
                        .build())
                .build());

        resources.add(DefaultResourceSpec.newBuilder()
                .name("disk")
                .preReservedRole(preReservedRole)
                .role(role)
                .principal(principal)
                .value(Protos.Value.newBuilder()
                        .setType(Protos.Value.Type.SCALAR)
                        .setScalar(Protos.Value.Scalar.newBuilder().setValue(256.0))
                        .build())
                .build());

        return resources;
    }

    private static String getPodRole(PodSpec podSpec) {
        return podSpec.getTasks().get(0).getResourceSet().getResources().stream().findFirst().get().getRole();
    }

    private static String getPodPrincipal(PodSpec podSpec) {
        return podSpec.getTasks().get(0).getResourceSet().getResources().stream().findFirst().get().getPrincipal();
    }
}
