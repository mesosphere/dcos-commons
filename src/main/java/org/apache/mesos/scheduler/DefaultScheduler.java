package org.apache.mesos.scheduler;

import org.apache.mesos.Protos;
import org.apache.mesos.Scheduler;
import org.apache.mesos.SchedulerDriver;
import org.apache.mesos.curator.CuratorStateStore;
import org.apache.mesos.specification.ServiceSpecification;
import org.apache.mesos.state.StateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Created by gabriel on 8/25/16.
 */
public class DefaultScheduler implements Scheduler {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final ServiceSpecification serviceSpecification;

    private StateStore stateStore;

    public DefaultScheduler(ServiceSpecification serviceSpecification) {
        this.serviceSpecification = serviceSpecification;
    }

    private void initialize() {
        logger.info("Initializing.");
        stateStore = new CuratorStateStore(serviceSpecification.getName());
    }

    @SuppressWarnings({"DM_EXIT"})
    private void hardExit(SchedulerErrorCode errorCode) {
        System.exit(errorCode.ordinal());
    }

    @Override
    public void registered(SchedulerDriver driver, Protos.FrameworkID frameworkId, Protos.MasterInfo masterInfo) {
        logger.info("Registered framework with frameworkId: " + frameworkId.getValue());
        initialize();

        try {
            stateStore.storeFrameworkId(frameworkId);
        } catch (Exception e) {
            logger.error(String.format(
                    "Unable to store registered framework ID '%s'", frameworkId.getValue()), e);
            hardExit(SchedulerErrorCode.REGISTRATION_FAILURE);
        }
    }

    @Override
    public void reregistered(SchedulerDriver driver, Protos.MasterInfo masterInfo) {

    }

    @Override
    public void resourceOffers(SchedulerDriver driver, List<Protos.Offer> offers) {

    }

    @Override
    public void offerRescinded(SchedulerDriver driver, Protos.OfferID offerId) {

    }

    @Override
    public void statusUpdate(SchedulerDriver driver, Protos.TaskStatus status) {

    }

    @Override
    public void frameworkMessage(SchedulerDriver driver, Protos.ExecutorID executorId, Protos.SlaveID slaveId, byte[] data) {

    }

    @Override
    public void disconnected(SchedulerDriver driver) {

    }

    @Override
    public void slaveLost(SchedulerDriver driver, Protos.SlaveID slaveId) {

    }

    @Override
    public void executorLost(SchedulerDriver driver, Protos.ExecutorID executorId, Protos.SlaveID slaveId, int status) {

    }

    @Override
    public void error(SchedulerDriver driver, String message) {

    }
}
