package com.mesosphere.sdk.scheduler;

import org.apache.mesos.Protos;

import java.util.List;

/**
 * Interface for schedulers to implement. Methods consist of callbacks that are invoked when the appropriate event is
 * received from the Mesos API stream.
 */
public interface Scheduler {

    void registered(V1SchedulerDriver driver, Protos.FrameworkID frameworkId, Protos.MasterInfo masterInfo);

    void reregistered(V1SchedulerDriver driver, Protos.MasterInfo masterInfo);

    void offerRescinded(V1SchedulerDriver driver, Protos.OfferID offerId);

    void statusUpdate(V1SchedulerDriver driver, Protos.TaskStatus status);

    void resourceOffers(V1SchedulerDriver driver, List<Protos.Offer> offers);

    void frameworkMessage(
            V1SchedulerDriver driver, Protos.ExecutorID executorId, Protos.SlaveID slaveId, byte[] data);

    void disconnected(V1SchedulerDriver driver);

    void slaveLost(V1SchedulerDriver driver, Protos.SlaveID slaveId);

    void executorLost(
            V1SchedulerDriver driver, Protos.ExecutorID executorId, Protos.SlaveID slaveId, int status);

    void error(V1SchedulerDriver driver, String message);
}
