package org.apache.mesos.scheduler;

import org.apache.mesos.SchedulerDriver;
import org.apache.mesos.v1.scheduler.Mesos;
import org.apache.mesos.v1.scheduler.Protos;

import java.util.Collection;

public class SchedulerDriverAdaptorMesos implements SchedulerDriver, Mesos {
    private Mesos mesos;

    public SchedulerDriverAdaptorMesos(Mesos mesos) {
        this.mesos = mesos;
    }

    @Override
    public void send(Protos.Call call) {
        mesos.send(call);
    }

    @Override
    public void reconnect() {

    }

    @Override
    public org.apache.mesos.Protos.Status start() {
        return null;
    }

    @Override
    public org.apache.mesos.Protos.Status stop(boolean failover) {
        return null;
    }

    @Override
    public org.apache.mesos.Protos.Status stop() {
        return null;
    }

    @Override
    public org.apache.mesos.Protos.Status abort() {
        return null;
    }

    @Override
    public org.apache.mesos.Protos.Status join() {
        return null;
    }

    @Override
    public org.apache.mesos.Protos.Status run() {
        return null;
    }

    @Override
    public org.apache.mesos.Protos.Status requestResources(Collection<org.apache.mesos.Protos.Request> requests) {
        return null;
    }

    @Override
    public org.apache.mesos.Protos.Status launchTasks(Collection<org.apache.mesos.Protos.OfferID> offerIds, Collection<org.apache.mesos.Protos.TaskInfo> tasks, org.apache.mesos.Protos.Filters filters) {
        return null;
    }

    @Override
    public org.apache.mesos.Protos.Status launchTasks(Collection<org.apache.mesos.Protos.OfferID> offerIds, Collection<org.apache.mesos.Protos.TaskInfo> tasks) {
        return null;
    }

    @Override
    public org.apache.mesos.Protos.Status launchTasks(org.apache.mesos.Protos.OfferID offerId, Collection<org.apache.mesos.Protos.TaskInfo> tasks, org.apache.mesos.Protos.Filters filters) {
        return null;
    }

    @Override
    public org.apache.mesos.Protos.Status launchTasks(org.apache.mesos.Protos.OfferID offerId, Collection<org.apache.mesos.Protos.TaskInfo> tasks) {
        return null;
    }

    @Override
    public org.apache.mesos.Protos.Status killTask(org.apache.mesos.Protos.TaskID taskId) {
        return null;
    }

    @Override
    public org.apache.mesos.Protos.Status acceptOffers(Collection<org.apache.mesos.Protos.OfferID> offerIds, Collection<org.apache.mesos.Protos.Offer.Operation> operations, org.apache.mesos.Protos.Filters filters) {
        return null;
    }

    @Override
    public org.apache.mesos.Protos.Status declineOffer(org.apache.mesos.Protos.OfferID offerId, org.apache.mesos.Protos.Filters filters) {
        return null;
    }

    @Override
    public org.apache.mesos.Protos.Status declineOffer(org.apache.mesos.Protos.OfferID offerId) {
        return null;
    }

    @Override
    public org.apache.mesos.Protos.Status reviveOffers() {
        return null;
    }

    @Override
    public org.apache.mesos.Protos.Status suppressOffers() {
        return null;
    }

    @Override
    public org.apache.mesos.Protos.Status acknowledgeStatusUpdate(org.apache.mesos.Protos.TaskStatus status) {
        return null;
    }

    @Override
    public org.apache.mesos.Protos.Status sendFrameworkMessage(org.apache.mesos.Protos.ExecutorID executorId, org.apache.mesos.Protos.SlaveID slaveId, byte[] data) {
        return null;
    }

    @Override
    public org.apache.mesos.Protos.Status reconcileTasks(Collection<org.apache.mesos.Protos.TaskStatus> statuses) {
        return null;
    }
}
