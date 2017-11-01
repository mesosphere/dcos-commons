package com.mesosphere.sdk.scheduler;

import com.mesosphere.sdk.dcos.Capabilities;
import com.mesosphere.sdk.offer.MesosResourcePool;
import com.mesosphere.sdk.offer.evaluate.DefaultExecutorVisitor;
import com.mesosphere.sdk.offer.evaluate.EvaluationOutcome;
import com.mesosphere.sdk.offer.evaluate.ExistingPodVisitor;
import com.mesosphere.sdk.offer.evaluate.HierarchicalReservationCreator;
import com.mesosphere.sdk.offer.evaluate.LaunchGroupVisitor;
import com.mesosphere.sdk.offer.evaluate.LegacyLaunchVisitor;
import com.mesosphere.sdk.offer.evaluate.LegacyReservationCreator;
import com.mesosphere.sdk.offer.evaluate.ReservationCreator;
import com.mesosphere.sdk.offer.evaluate.SpecVisitor;
import com.mesosphere.sdk.offer.evaluate.NullVisitor;
import com.mesosphere.sdk.offer.evaluate.OfferConsumptionVisitor;
import org.apache.mesos.Protos;

import java.util.Collection;
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

    public SpecVisitor<EvaluationOutcome> getEvaluationVisitor(
            MesosResourcePool mesosResourcePool,
            Collection<Protos.TaskInfo> podTasks,
            Collection<Protos.TaskInfo> runningPodTasks,
            Collection<Protos.TaskInfo> allTasks) {
        SpecVisitor<EvaluationOutcome> launchOperationVisitor =
                getLaunchOperationVisitor(mesosResourcePool, podTasks, allTasks, !runningPodTasks.isEmpty(), null);
        SpecVisitor<EvaluationOutcome> offerConsumptionVisitor =
                getOfferConsumptionVisitor(mesosResourcePool, runningPodTasks, launchOperationVisitor);
        SpecVisitor<EvaluationOutcome> existingPodVisitor =
                getExistingPodVisitor(mesosResourcePool, podTasks, offerConsumptionVisitor);
        SpecVisitor<EvaluationOutcome> executorVisitor = getExecutorVisitor(existingPodVisitor);

        return executorVisitor;
    }

    public SpecVisitor<EvaluationOutcome> getExecutorVisitor(SpecVisitor delegate) {
        if (capabilities.supportsDefaultExecutor()) {
            return new DefaultExecutorVisitor(delegate);
        }

        return new NullVisitor(delegate);
    }

    public SpecVisitor<EvaluationOutcome> getExistingPodVisitor(
            MesosResourcePool mesosResourcePool, Collection<Protos.TaskInfo> taskInfos, SpecVisitor delegate) {
        return new ExistingPodVisitor(mesosResourcePool, taskInfos, getReservationCreator(), delegate);
    }

    public SpecVisitor<EvaluationOutcome> getOfferConsumptionVisitor(
            MesosResourcePool mesosResourcePool, Collection<Protos.TaskInfo> runningTasks, SpecVisitor delegate) {
        return new OfferConsumptionVisitor(mesosResourcePool, getReservationCreator(), runningTasks, delegate);
    }

    public SpecVisitor<EvaluationOutcome> getLaunchOperationVisitor(
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

        return new LegacyLaunchVisitor(
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
