package com.mesosphere.sdk.scheduler;

import com.mesosphere.sdk.dcos.Capabilities;
import com.mesosphere.sdk.offer.MesosResourcePool;
import com.mesosphere.sdk.offer.evaluate.DefaultExecutorVisitor;
import com.mesosphere.sdk.offer.evaluate.EvaluationOutcome;
import com.mesosphere.sdk.offer.evaluate.ExistingPodVisitor;
import com.mesosphere.sdk.offer.evaluate.HierarchicalReservationCreator;
import com.mesosphere.sdk.offer.evaluate.LaunchGroupVisitor;
import com.mesosphere.sdk.offer.evaluate.LaunchVisitor;
import com.mesosphere.sdk.offer.evaluate.LegacyReservationCreator;
import com.mesosphere.sdk.offer.evaluate.ReservationCreator;
import com.mesosphere.sdk.offer.evaluate.SpecVisitor;
import com.mesosphere.sdk.offer.evaluate.VisitorResultCollector;
import com.mesosphere.sdk.offer.evaluate.placement.NullVisitor;
import com.mesosphere.sdk.offer.evaluate.placement.OfferConsumptionVisitor;
import org.apache.mesos.Protos;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The OfferEvaluationComponentFactory sniffs the environment with a {@link Capabilities} object, and coordinates the
 * construction of any object involved in offer evaluation that has a capability-dependent implementation.
 */
public class OfferEvaluationComponentFactory {
    private final Capabilities capabilities;
    private final String serviceName;
    private final Protos.FrameworkID frameworkID;
    private final UUID targetConfigurationId;
    private final SchedulerConfig schedulerConfig;

    private ReservationCreator reservationCreator;

    public OfferEvaluationComponentFactory(
            Capabilities capabilities,
            String serviceName,
            Protos.FrameworkID frameworkID,
            UUID targetConfigurationId,
            SchedulerConfig schedulerFlags) {
        this.capabilities = capabilities;
        this.serviceName = serviceName;
        this.frameworkID = frameworkID;
        this.targetConfigurationId = targetConfigurationId;
        this.schedulerConfig = schedulerFlags;
    }

    public ReservationCreator getReservationCreator() {
        if (reservationCreator == null) {
            if (capabilities.supportsPreReservedResources()) {
                reservationCreator = new HierarchicalReservationCreator();
            } else {
                reservationCreator = new LegacyReservationCreator();
            }
        }

        return reservationCreator;
    }

    public SpecVisitor<VisitorResultCollector.Empty> getExecutorVisitor(SpecVisitor delegate) {
        if (capabilities.supportsDefaultExecutor()) {
            return new DefaultExecutorVisitor(delegate);
        }

        return new NullVisitor(delegate);
    }

    public ExistingPodVisitor getExistingPodVisitor(
            MesosResourcePool mesosResourcePool, Collection<Protos.TaskInfo> taskInfos, SpecVisitor delegate) {
        return new ExistingPodVisitor(mesosResourcePool, taskInfos, getReservationCreator(), delegate);
    }

    public OfferConsumptionVisitor getOfferConsumptionVisitor(
            MesosResourcePool mesosResourcePool, Collection<Protos.TaskInfo> runningTasks, SpecVisitor delegate) {
        return new OfferConsumptionVisitor(mesosResourcePool, getReservationCreator(), runningTasks, delegate);
    }

    public SpecVisitor<List<EvaluationOutcome>> getLaunchOperationVisitor(
            MesosResourcePool mesosResourcePool,
            Collection<Protos.TaskInfo> podTasks,
            Collection<Protos.TaskInfo> allTasks,
            boolean isRunning,
            SpecVisitor delegate) {
        if (capabilities.supportsDefaultExecutor()) {
            return new LaunchGroupVisitor(
                    podTasks,
                    allTasks,
                    mesosResourcePool.getOffer(),
                    serviceName,
                    frameworkID,
                    targetConfigurationId,
                    schedulerConfig,
                    isRunning,
                    delegate);
        }

        return new LaunchVisitor(
                podTasks,
                allTasks,
                mesosResourcePool.getOffer(),
                serviceName,
                frameworkID,
                targetConfigurationId,
                schedulerConfig,
                isRunning,
                delegate);
    }
}
