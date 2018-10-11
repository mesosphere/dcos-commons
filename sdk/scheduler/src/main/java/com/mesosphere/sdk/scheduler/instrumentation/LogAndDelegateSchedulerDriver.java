package com.mesosphere.sdk.scheduler.instrumentation;

import com.mesosphere.sdk.scheduler.Driver;
import org.apache.mesos.Protos;
import org.apache.mesos.SchedulerDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

public class LogAndDelegateSchedulerDriver implements SchedulerDriver {
    private static final Logger LOGGER = LoggerFactory.getLogger(LogAndDelegateSchedulerDriver.class);
    private final SchedulerDriver targetDriver;

    public LogAndDelegateSchedulerDriver(SchedulerDriver targetDriver) {
        this.targetDriver = targetDriver;
    }

    private void log(String methodName) {
        LOGGER.info("SCHEDULER CALL: {}", methodName);
    }

    private void log(String methodName, String extraInfo) {
        LOGGER.info("SCHEDULER CALL: {}({})", methodName, extraInfo);
    }

    @Override
    public Protos.Status start() {
        log("start");
        return targetDriver.start();
    }

    @Override
    public Protos.Status stop(boolean failover) {
        log("stop");
        return targetDriver.stop(failover);
    }

    @Override
    public Protos.Status stop() {
        log("stop");
        return targetDriver.stop();
    }

    @Override
    public Protos.Status abort() {
        log("abort");
        return targetDriver.abort();
    }

    @Override
    public Protos.Status join() {
        log("join");
        return targetDriver.join();
    }

    @Override
    public Protos.Status run() {
        log("run");
        return targetDriver.run();
    }

    @Override
    public Protos.Status requestResources(Collection<Protos.Request> requests) {
        log("requestResources");
        return targetDriver.requestResources(requests);
    }

    @Override
    public Protos.Status launchTasks(Collection<Protos.OfferID> offerIds, Collection<Protos.TaskInfo> tasks, Protos.Filters filters) {
        log("launchTasks");
        return targetDriver.launchTasks(offerIds, tasks, filters);
    }

    @Override
    public Protos.Status launchTasks(Collection<Protos.OfferID> offerIds, Collection<Protos.TaskInfo> tasks) {
        log("launchTasks");
        return targetDriver.launchTasks(offerIds, tasks);
    }

    @SuppressWarnings("deprecation")
    @Override
    public Protos.Status launchTasks(Protos.OfferID offerId, Collection<Protos.TaskInfo> tasks, Protos.Filters filters) {
        log("launchTasks");
        return targetDriver.launchTasks(offerId, tasks, filters);
    }

    @SuppressWarnings("deprecation")
    @Override
    public Protos.Status launchTasks(Protos.OfferID offerId, Collection<Protos.TaskInfo> tasks) {
        log("launchTasks");
        return targetDriver.launchTasks(offerId, tasks);
    }

    @Override
    public Protos.Status killTask(Protos.TaskID taskId) {
        log("killTask");
        return targetDriver.killTask(taskId);
    }

    @Override
    public Protos.Status acceptOffers(Collection<Protos.OfferID> offerIds, Collection<Protos.Offer.Operation> operations, Protos.Filters filters) {
        log("acceptOffers", String.format("%d offers, %d operations, refuse %f seconds", offerIds.size(), operations.size(), filters.getRefuseSeconds()));
        return targetDriver.acceptOffers(offerIds, operations, filters);
    }

    @Override
    public Protos.Status declineOffer(Protos.OfferID offerId, Protos.Filters filters) {
        log("declineOffer");
        return targetDriver.declineOffer(offerId, filters);
    }

    @Override
    public Protos.Status declineOffer(Protos.OfferID offerId) {
        log("declineOffer");
        return targetDriver.declineOffer(offerId);
    }

    @Override
    public Protos.Status reviveOffers() {
        log("reviveOffers");
        return targetDriver.reviveOffers();
    }

    @Override
    public Protos.Status suppressOffers() {
        log("suppressOffers");
        return targetDriver.suppressOffers();
    }

    @Override
    public Protos.Status acknowledgeStatusUpdate(Protos.TaskStatus status) {
        log("acknowledgeStatusUpdate");
        return targetDriver.acknowledgeStatusUpdate(status);
    }

    @Override
    public Protos.Status sendFrameworkMessage(Protos.ExecutorID executorId, Protos.SlaveID slaveId, byte[] data) {
        log("sendFrameworkMessage");
        return targetDriver.sendFrameworkMessage(executorId, slaveId, data);
    }

    @Override
    public Protos.Status reconcileTasks(Collection<Protos.TaskStatus> statuses) {
        log("reconcileTasks");
        return targetDriver.reconcileTasks(statuses);
    }
}
