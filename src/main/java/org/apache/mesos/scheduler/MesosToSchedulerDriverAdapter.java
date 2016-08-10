package org.apache.mesos.scheduler;

import com.google.protobuf.ByteString;

import org.apache.mesos.SchedulerDriver;
import org.apache.mesos.protobuf.Evolver;
import org.apache.mesos.Protos.FrameworkID;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.TaskID;
import org.apache.mesos.scheduler.Protos.Call;
import org.apache.mesos.v1.scheduler.Mesos;


import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Adaptor class.
 */
public class SchedulerDriverAdaptorMesos implements SchedulerDriver, Mesos {
    private Mesos mesos;
    private FrameworkID frameworkId;

    public SchedulerDriverAdaptorMesos(Mesos mesos, FrameworkID frameworkId) {
        this.mesos = mesos;
        this.frameworkId = frameworkId;
    }

    @Override
    public void send(org.apache.mesos.v1.scheduler.Protos.Call call) {
        // TODO(anand): Throw exception?
    }

    @Override
    public void reconnect() {
        mesos.reconnect();
    }

    @Override
    public org.apache.mesos.Protos.Status start() {
        // TODO(anand): Initialize Mesos here.
        return null;
    }

    @Override
    public org.apache.mesos.Protos.Status stop(boolean failover) {
        return null;
    }

    @Override
    public org.apache.mesos.Protos.Status stop() {
        // TODO(anand): Fix the return?
        return null;
    }

    @Override
    public org.apache.mesos.Protos.Status abort() {
        // TODO(anand): Fix the return?
        return null;
    }

    @Override
    public org.apache.mesos.Protos.Status join() {
        // TODO(anand): Fix the return?
        return null;
    }

    @Override
    public org.apache.mesos.Protos.Status run() {
        // TODO(anand): Fix the return?
        return null;
    }

    @Override
    public org.apache.mesos.Protos.Status requestResources(Collection<org.apache.mesos.Protos.Request> requests) {
        mesos.send(Evolver.evolve(Call.newBuilder()
                .setType(Call.Type.REQUEST)
                .setFrameworkId(frameworkId)
                .setRequest(Call.Request.newBuilder()
                        .addAllRequests(requests)
                        .build())
                .build()));

        return org.apache.mesos.Protos.Status.DRIVER_RUNNING;
    }

    @Override
    public org.apache.mesos.Protos.Status launchTasks(Collection<org.apache.mesos.Protos.OfferID> offerIds,
                                                      Collection<org.apache.mesos.Protos.TaskInfo> tasks,
                                                      org.apache.mesos.Protos.Filters filters) {
        mesos.send(Evolver.evolve(Call.newBuilder()
                .setType(Call.Type.ACCEPT)
                .setFrameworkId(frameworkId)
                .setAccept(Call.Accept.newBuilder()
                        .addAllOfferIds(offerIds)
                        .addOperations(Offer.Operation.newBuilder()
                                .setType(Offer.Operation.Type.LAUNCH)
                                .setLaunch(Offer.Operation.Launch.newBuilder()
                                        .addAllTaskInfos(tasks)))
                        .build())
                .build()));

        return org.apache.mesos.Protos.Status.DRIVER_RUNNING;
    }

    @Override
    public org.apache.mesos.Protos.Status launchTasks(Collection<org.apache.mesos.Protos.OfferID> offerIds,
                                                      Collection<org.apache.mesos.Protos.TaskInfo> tasks) {
        mesos.send(Evolver.evolve(Call.newBuilder()
                .setType(Call.Type.ACCEPT)
                .setFrameworkId(frameworkId)
                .setAccept(Call.Accept.newBuilder()
                        .addAllOfferIds(offerIds)
                        .addOperations(Offer.Operation.newBuilder()
                                .setType(Offer.Operation.Type.LAUNCH)
                                .setLaunch(Offer.Operation.Launch.newBuilder()
                                        .addAllTaskInfos(tasks)))
                        .build())
                .build()));

        return org.apache.mesos.Protos.Status.DRIVER_RUNNING;
    }

    @Override
    public org.apache.mesos.Protos.Status launchTasks(org.apache.mesos.Protos.OfferID offerId,
                                                      Collection<org.apache.mesos.Protos.TaskInfo> tasks,
                                                      org.apache.mesos.Protos.Filters filters) {
        mesos.send(Evolver.evolve(Call.newBuilder()
                .setType(Call.Type.ACCEPT)
                .setFrameworkId(frameworkId)
                .setAccept(Call.Accept.newBuilder()
                        .addOfferIds(offerId)
                        .addOperations(Offer.Operation.newBuilder()
                                .setType(Offer.Operation.Type.LAUNCH)
                                .setLaunch(Offer.Operation.Launch.newBuilder()
                                        .addAllTaskInfos(tasks))
                                .build())
                        .setFilters(filters))
                .build()));

        return org.apache.mesos.Protos.Status.DRIVER_RUNNING;
    }

    @Override
    public org.apache.mesos.Protos.Status launchTasks(org.apache.mesos.Protos.OfferID offerId,
                                                      Collection<org.apache.mesos.Protos.TaskInfo> tasks) {
        mesos.send(Evolver.evolve(Call.newBuilder()
                .setType(Call.Type.ACCEPT)
                .setFrameworkId(frameworkId)
                .setAccept(Call.Accept.newBuilder()
                        .addOfferIds(offerId)
                        .addOperations(Offer.Operation.newBuilder()
                                .setType(Offer.Operation.Type.LAUNCH)
                                .setLaunch(Offer.Operation.Launch.newBuilder()
                                        .addAllTaskInfos(tasks)))
                        .build())
                .build()));

        return org.apache.mesos.Protos.Status.DRIVER_RUNNING;
    }

    @Override
    public org.apache.mesos.Protos.Status killTask(org.apache.mesos.Protos.TaskID taskId) {
        mesos.send(Evolver.evolve(Call.newBuilder()
                .setType(Call.Type.KILL)
                .setFrameworkId(frameworkId)
                .setKill(Call.Kill.newBuilder()
                        .setTaskId(taskId)
                        .build())
                .build()));

        return org.apache.mesos.Protos.Status.DRIVER_RUNNING;
    }

    @Override
    public org.apache.mesos.Protos.Status acceptOffers(Collection<org.apache.mesos.Protos.OfferID> offerIds,
                                                       Collection<org.apache.mesos.Protos.Offer.Operation> operations,
                                                       org.apache.mesos.Protos.Filters filters) {
        mesos.send(Evolver.evolve(Call.newBuilder()
                .setType(Call.Type.ACCEPT)
                .setFrameworkId(frameworkId)
                .setAccept(Call.Accept.newBuilder()
                        .addAllOfferIds(offerIds)
                        .addAllOperations(operations)
                        .setFilters(filters)
                        .build())
                .build()));

        return org.apache.mesos.Protos.Status.DRIVER_RUNNING;
    }

    @Override
    public org.apache.mesos.Protos.Status declineOffer(org.apache.mesos.Protos.OfferID offerId,
                                                       org.apache.mesos.Protos.Filters filters) {
        mesos.send(Evolver.evolve(Call.newBuilder()
                .setType(Call.Type.DECLINE)
                .setFrameworkId(frameworkId)
                .setDecline(Call.Decline.newBuilder()
                        .addOfferIds(offerId)
                        .setFilters(filters)
                        .build())
                .build()));

        return org.apache.mesos.Protos.Status.DRIVER_RUNNING;
    }

    @Override
    public org.apache.mesos.Protos.Status declineOffer(org.apache.mesos.Protos.OfferID offerId) {
        mesos.send(Evolver.evolve(Call.newBuilder()
                .setType(Call.Type.DECLINE)
                .setFrameworkId(frameworkId)
                .setDecline(Call.Decline.newBuilder()
                        .addOfferIds(offerId)
                        .build())
                .build()));

        return org.apache.mesos.Protos.Status.DRIVER_RUNNING;
    }

    @Override
    public org.apache.mesos.Protos.Status reviveOffers() {
        mesos.send(Evolver.evolve(Call.newBuilder()
                .setType(Call.Type.REVIVE)
                .setFrameworkId(frameworkId)
                .build()));

        return org.apache.mesos.Protos.Status.DRIVER_RUNNING;
    }

    @Override
    public org.apache.mesos.Protos.Status suppressOffers() {
        mesos.send(Evolver.evolve(Call.newBuilder()
                .setType(Call.Type.SUPPRESS)
                .setFrameworkId(frameworkId)
                .build()));

        return org.apache.mesos.Protos.Status.DRIVER_RUNNING;
    }

    @Override
    public org.apache.mesos.Protos.Status acknowledgeStatusUpdate(org.apache.mesos.Protos.TaskStatus status) {
        mesos.send(Evolver.evolve(Call.newBuilder()
                .setType(Call.Type.ACKNOWLEDGE)
                .setFrameworkId(frameworkId)
                .setAcknowledge(Call.Acknowledge.newBuilder()
                        .setSlaveId(status.getSlaveId())
                        .setTaskId(status.getTaskId())
                        .setUuid(status.getUuid())
                        .build())
                .build()));

        return org.apache.mesos.Protos.Status.DRIVER_RUNNING;
    }

    @Override
    public org.apache.mesos.Protos.Status sendFrameworkMessage(org.apache.mesos.Protos.ExecutorID executorId,
                                                               org.apache.mesos.Protos.SlaveID slaveId,
                                                               byte[] data) {
        mesos.send(Evolver.evolve(Call.newBuilder()
                .setType(Call.Type.MESSAGE)
                .setFrameworkId(frameworkId)
                .setMessage(Call.Message.newBuilder()
                        .setData(ByteString.copyFrom(data))
                        .build())
                .build()));

        return org.apache.mesos.Protos.Status.DRIVER_RUNNING;
    }

    @Override
    public org.apache.mesos.Protos.Status reconcileTasks(Collection<org.apache.mesos.Protos.TaskStatus> statuses) {
        List<Call.Reconcile.Task> tasks = new ArrayList<>();
        for (org.apache.mesos.Protos.TaskStatus status: statuses) {
            Call.Reconcile.Task.Builder builder = Call.Reconcile.Task.newBuilder();
            builder.setTaskId(status.getTaskId());

            if (status.hasSlaveId()) {
                builder.setSlaveId(status.getSlaveId());
            }

            tasks.add(builder.build());
        }

        mesos.send(Evolver.evolve(Call.newBuilder()
                .setType(Call.Type.RECONCILE)
                .setFrameworkId(frameworkId)
                .setReconcile(Call.Reconcile.newBuilder()
                        .addAllTasks(tasks)
                        .build())
                .build()));

        return org.apache.mesos.Protos.Status.DRIVER_RUNNING;
    }
}
