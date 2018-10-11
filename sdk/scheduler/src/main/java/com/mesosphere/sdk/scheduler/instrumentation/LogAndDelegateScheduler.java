package com.mesosphere.sdk.scheduler.instrumentation;

import com.mesosphere.sdk.scheduler.SchedulerRunner;
import org.apache.mesos.Protos;
import org.apache.mesos.Scheduler;
import org.apache.mesos.SchedulerDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class LogAndDelegateScheduler implements Scheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(LogAndDelegateScheduler.class);
    private final Scheduler targetScheduler;

    public LogAndDelegateScheduler(Scheduler targetScheduler) {
        this.targetScheduler = targetScheduler;
    }

    private void log(String methodName) {
        LOGGER.info("SCHEDULER CALLBACK: {}", methodName);
    }

    private void log(String methodName, String extraInfo) {
        LOGGER.info("SCHEDULER CALLBACK: {}({})", methodName, extraInfo);
    }

    @Override
    public void registered(SchedulerDriver driver, Protos.FrameworkID frameworkId, Protos.MasterInfo masterInfo) {
        log("registered");
        targetScheduler.registered(driver, frameworkId, masterInfo);
    }

    @Override
    public void reregistered(SchedulerDriver driver, Protos.MasterInfo masterInfo) {
        log("re-registered");
        targetScheduler.reregistered(driver, masterInfo);
    }

    @Override
    public void resourceOffers(SchedulerDriver driver, List<Protos.Offer> offers) {
        log("resourceOffers", String.format("%d offers", offers.size()));
        targetScheduler.resourceOffers(driver, offers);
    }

    @Override
    public void offerRescinded(SchedulerDriver driver, Protos.OfferID offerId) {
        log("offerRescinded");
        targetScheduler.offerRescinded(driver, offerId);
    }

    @Override
    public void statusUpdate(SchedulerDriver driver, Protos.TaskStatus status) {
        log("statusUpdate", String.format("%s:%s", status.getState().toString(), status.getMessage()));
        targetScheduler.statusUpdate(driver, status);
    }

    @Override
    public void frameworkMessage(SchedulerDriver driver, Protos.ExecutorID executorId, Protos.SlaveID slaveId, byte[] data) {
        log("frameworkMessage");
        targetScheduler.frameworkMessage(driver, executorId, slaveId, data);
    }

    @Override
    public void disconnected(SchedulerDriver driver) {
        log("disconnected");
        targetScheduler.disconnected(driver);
    }

    @Override
    public void slaveLost(SchedulerDriver driver, Protos.SlaveID slaveId) {
        log("slaveLost");
        targetScheduler.slaveLost(driver, slaveId);
    }

    @Override
    public void executorLost(SchedulerDriver driver, Protos.ExecutorID executorId, Protos.SlaveID slaveId, int status) {
        log("executorLost");
        targetScheduler.executorLost(driver, executorId, slaveId, status);
    }

    @Override
    public void error(SchedulerDriver driver, String message) {
        log("error");
        targetScheduler.error(driver, message);
    }
}
