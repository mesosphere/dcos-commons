package org.apache.mesos.scheduler;

import com.google.protobuf.ByteString;
import org.apache.mesos.protobuf.Devolver;
import org.apache.mesos.protobuf.Evolver;
import org.apache.mesos.v1.scheduler.JNIMesos;
import org.apache.mesos.v1.scheduler.Mesos;
import org.apache.mesos.v1.scheduler.Protos;
import org.apache.mesos.v1.scheduler.V0Mesos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * This is an adapter from the new v1 `Mesos` + `Scheduler` interface to the old  v0 `SchedulerDriver` + `Scheduler`
 * interface. It intercepts:
 * - The v1 scheduler callbacks and converts them into appropriate v0 scheduler callbacks.
 * - The various `driver.xx()` calls, creates a `Call` message and then invokes `send()` on the v1
 *   `Mesos` interface.
 */
public class MesosToSchedulerDriverAdapter implements
            org.apache.mesos.v1.scheduler.Scheduler,
            org.apache.mesos.SchedulerDriver {

    private static final Logger LOGGER = LoggerFactory.getLogger(MesosToSchedulerDriverAdapter.class);

    // Exponential back-off between consecutive subscribe calls.
    private static final int MULTIPLIER = 2;
    private static final long SEED_BACKOFF_MS = 2000;
    private static final long MAX_BACKOFF_MS = 30000;

    private org.apache.mesos.Scheduler wrappedScheduler;
    private org.apache.mesos.v1.Protos.FrameworkInfo frameworkInfo;
    private final String master;
    private final org.apache.mesos.v1.Protos.Credential credential;
    private boolean registered;
    private Mesos mesos;
    private Timer subscriberTimer;
    private long backOffMs;

    public MesosToSchedulerDriverAdapter(org.apache.mesos.Scheduler wrappedScheduler,
                                         org.apache.mesos.Protos.FrameworkInfo frameworkInfo,
                                         String master) {
        this.wrappedScheduler = wrappedScheduler;
        this.frameworkInfo = Evolver.evolve(frameworkInfo);
        this.master = master;
        this.credential = null;
        this.registered = false;
    }

    public MesosToSchedulerDriverAdapter(org.apache.mesos.Scheduler wrappedScheduler,
                                         org.apache.mesos.Protos.FrameworkInfo frameworkInfo,
                                         String master,
                                         org.apache.mesos.Protos.Credential credential) {
        this.wrappedScheduler = wrappedScheduler;
        this.frameworkInfo = Evolver.evolve(frameworkInfo);
        this.master = master;
        this.credential = Evolver.evolve(credential);
        this.registered = false;
    }

    @Override
    public void connected(Mesos mesos) {
        LOGGER.info("Connected!");
        performReliableSubscription(mesos);
    }

    /**
     * Task that performs Subscription.
     */
    public class SubscriberTask extends TimerTask {
        private Mesos mesos;

        public SubscriberTask(Mesos mesos) {
            this.mesos = mesos;
        }

        @Override
        public void run() {
            LOGGER.info("Sending SUBSCRIBE call");
            Protos.Call.Builder callBuilder = Protos.Call.newBuilder()
                    .setType(Protos.Call.Type.SUBSCRIBE)
                    .setSubscribe(Protos.Call.Subscribe.newBuilder()
                            .setFrameworkInfo(frameworkInfo)
                            .build());
            if (frameworkInfo.hasId()) {
                callBuilder.setFrameworkId(frameworkInfo.getId());
            }
            mesos.send(callBuilder.build());
            long nextBackoff = MULTIPLIER * backOffMs;
            backOffMs = Math.min(nextBackoff > 0 ? nextBackoff : 0, MAX_BACKOFF_MS);
            LOGGER.info("Backing off for: {}ms", backOffMs);
            subscriberTimer.schedule(new SubscriberTask(mesos), backOffMs);
        }
    }

    private void performReliableSubscription(Mesos mesos) {
        // If timer is not running, initialize it.
        if (subscriberTimer == null) {
            initSubscriber();
            subscriberTimer.schedule(new SubscriberTask(mesos), backOffMs);
        }
    }

    private void initSubscriber() {
        LOGGER.info("Initializing reliable subscriber...");
        backOffMs = SEED_BACKOFF_MS;
        subscriberTimer = new Timer();
    }

    private void cancelSubscriber() {
        LOGGER.info("Cancelling subscriber...");
        if (subscriberTimer != null) {
            subscriberTimer.cancel();
            subscriberTimer.purge();
        }
        subscriberTimer = null;
    }

    @Override
    public void disconnected(Mesos mesos) {
        LOGGER.info("Disconnected!");
        cancelSubscriber();
    }

    @Override
    public void received(Mesos mesos, Protos.Event v1Event) {
        org.apache.mesos.scheduler.Protos.Event event = Devolver.devolve(v1Event);
        LOGGER.info("Received event: {}", event);

        switch (event.getType()) {
            case SUBSCRIBED: {
                cancelSubscriber();
                frameworkInfo = org.apache.mesos.v1.Protos.FrameworkInfo.newBuilder(frameworkInfo)
                        .setId(v1Event.getSubscribed().getFrameworkId())
                        .build();

                if (!registered) {
                    registered = true;
                    wrappedScheduler.registered(
                            this,
                            Devolver.devolve(frameworkInfo.getId()),
                            null /* MasterInfo */);
                } else {
                    wrappedScheduler.reregistered(this, null /* MasterInfo */);
                }

                // TODO(mohit): Change state to SUBSCRIBED

                LOGGER.info("Subscribed with ID " + frameworkInfo.getId());
                break;
            }

            case OFFERS: {
                wrappedScheduler.resourceOffers(this, event.getOffers().getOffersList());
                break;
            }

            case INVERSE_OFFERS:
                break;

            case RESCIND: {
                wrappedScheduler.offerRescinded(this, event.getRescind().getOfferId());
                break;
            }

            case RESCIND_INVERSE_OFFER:
                break;

            case UPDATE: {
                final org.apache.mesos.v1.Protos.TaskStatus v1Status = v1Event.getUpdate().getStatus();

                wrappedScheduler.statusUpdate(this, event.getUpdate().getStatus());

                // Send ack only when UUID is set.
                if (v1Status.hasUuid()) {
                    final org.apache.mesos.v1.Protos.AgentID agentId = v1Status.getAgentId();
                    final org.apache.mesos.v1.Protos.TaskID taskId = v1Status.getTaskId();

                    mesos.send(Protos.Call.newBuilder()
                            .setType(Protos.Call.Type.ACKNOWLEDGE)
                            .setFrameworkId(frameworkInfo.getId())
                            .setAcknowledge(Protos.Call.Acknowledge.newBuilder()
                                    .setAgentId(agentId)
                                    .setTaskId(taskId)
                                    .setUuid(v1Status.getUuid())
                                    .build())
                            .build());
                }
                break;
            }

            case MESSAGE: {
                wrappedScheduler.frameworkMessage(
                        this,
                        event.getMessage().getExecutorId(),
                        event.getMessage().getSlaveId(),
                        event.getMessage().getData().toByteArray());
                break;
            }

            case FAILURE: {
                final org.apache.mesos.scheduler.Protos.Event.Failure failure = event.getFailure();
                if (failure.hasSlaveId() && failure.hasExecutorId()) {
                    wrappedScheduler.executorLost(
                            this,
                            failure.getExecutorId(),
                            failure.getSlaveId(),
                            failure.getStatus());
                } else {
                    wrappedScheduler.slaveLost(this, failure.getSlaveId());
                }
                break;
            }

            case ERROR: {
                final org.apache.mesos.scheduler.Protos.Event.Error error = event.getError();
                wrappedScheduler.error(this, error.getMessage());
                break;
            }

            case HEARTBEAT: {
                // TODO(Mohit): Add handler for acting on lack of heartbeats.
                break;
            }

            case UNKNOWN: {
                LOGGER.error("Received an unsupported event: {}", event);
                break;
            }

            default: {
                LOGGER.error("Received an unsupported event: {}", event);
                break;
            }
        }
    }

    @Override
    public org.apache.mesos.Protos.Status start() {
        // TODO(mohit): Prevent more than 1x start() invocation.
        String mesosApi = System.getenv("MESOS_API_VERSION");
        if (mesosApi == null) {
            mesosApi = "V0";
        }

        LOGGER.info("Using Mesos API version: {}", mesosApi);

        if (mesosApi.equals("V0")){
            if (credential == null) {
                this.mesos = new V0Mesos(this, frameworkInfo, master);
            } else {
                this.mesos = new V0Mesos(this, frameworkInfo, master, credential);
            }
        } else if (mesosApi.equals("V1")) {
            if (credential == null) {
                this.mesos = new JNIMesos(this, master);
            } else {
                this.mesos = new JNIMesos(this, master, credential);
            }
        } else {
            throw new IllegalArgumentException("Unsupported API version: " + mesosApi);
        }

        return org.apache.mesos.Protos.Status.DRIVER_RUNNING;
    }

    @Override
    public org.apache.mesos.Protos.Status stop(boolean failover) {
        if (!failover) {
            mesos.send(org.apache.mesos.v1.scheduler.Protos.Call.newBuilder()
                    .setType(org.apache.mesos.v1.scheduler.Protos.Call.Type.TEARDOWN)
                    .setFrameworkId(frameworkInfo.getId())
                    .build());
        }

        this.mesos = null;

        return org.apache.mesos.Protos.Status.DRIVER_STOPPED;
    }

    @Override
    public org.apache.mesos.Protos.Status stop() {
        return stop(false);
    }

    @Override
    public org.apache.mesos.Protos.Status abort() {
        this.mesos = null;

        return org.apache.mesos.Protos.Status.DRIVER_ABORTED;
    }

    @Override
    public org.apache.mesos.Protos.Status join() {
        throw new UnsupportedOperationException();
    }

    @Override
    public org.apache.mesos.Protos.Status run() {
        throw new UnsupportedOperationException();
    }

    @Override
    public org.apache.mesos.Protos.Status requestResources(Collection<org.apache.mesos.Protos.Request> requests) {
        mesos.send(Evolver.evolve(org.apache.mesos.scheduler.Protos.Call.newBuilder()
                .setType(org.apache.mesos.scheduler.Protos.Call.Type.REQUEST)
                .setFrameworkId(Devolver.devolve(frameworkInfo.getId()))
                .setRequest(org.apache.mesos.scheduler.Protos.Call.Request.newBuilder()
                        .addAllRequests(requests)
                        .build())
                .build()));

        return org.apache.mesos.Protos.Status.DRIVER_RUNNING;
    }

    @Override
    public org.apache.mesos.Protos.Status launchTasks(Collection<org.apache.mesos.Protos.OfferID> offerIds,
                                                      Collection<org.apache.mesos.Protos.TaskInfo> tasks,
                                                      org.apache.mesos.Protos.Filters filters) {
        mesos.send(Evolver.evolve(org.apache.mesos.scheduler.Protos.Call.newBuilder()
                .setType(org.apache.mesos.scheduler.Protos.Call.Type.ACCEPT)
                .setFrameworkId(Devolver.devolve(frameworkInfo.getId()))
                .setAccept(org.apache.mesos.scheduler.Protos.Call.Accept.newBuilder()
                        .addAllOfferIds(offerIds)
                        .addOperations(org.apache.mesos.Protos.Offer.Operation.newBuilder()
                                .setType(org.apache.mesos.Protos.Offer.Operation.Type.LAUNCH)
                                .setLaunch(org.apache.mesos.Protos.Offer.Operation.Launch.newBuilder()
                                        .addAllTaskInfos(tasks)))
                        .build())
                .build()));

        return org.apache.mesos.Protos.Status.DRIVER_RUNNING;
    }

    @Override
    public org.apache.mesos.Protos.Status launchTasks(Collection<org.apache.mesos.Protos.OfferID> offerIds,
                                                      Collection<org.apache.mesos.Protos.TaskInfo> tasks) {
        mesos.send(Evolver.evolve(org.apache.mesos.scheduler.Protos.Call.newBuilder()
                .setType(org.apache.mesos.scheduler.Protos.Call.Type.ACCEPT)
                .setFrameworkId(Devolver.devolve(frameworkInfo.getId()))
                .setAccept(org.apache.mesos.scheduler.Protos.Call.Accept.newBuilder()
                        .addAllOfferIds(offerIds)
                        .addOperations(org.apache.mesos.Protos.Offer.Operation.newBuilder()
                                .setType(org.apache.mesos.Protos.Offer.Operation.Type.LAUNCH)
                                .setLaunch(org.apache.mesos.Protos.Offer.Operation.Launch.newBuilder()
                                        .addAllTaskInfos(tasks)))
                        .build())
                .build()));

        return org.apache.mesos.Protos.Status.DRIVER_RUNNING;
    }

    @Override
    public org.apache.mesos.Protos.Status launchTasks(org.apache.mesos.Protos.OfferID offerId,
                                                      Collection<org.apache.mesos.Protos.TaskInfo> tasks,
                                                      org.apache.mesos.Protos.Filters filters) {
        mesos.send(Evolver.evolve(org.apache.mesos.scheduler.Protos.Call.newBuilder()
                .setType(org.apache.mesos.scheduler.Protos.Call.Type.ACCEPT)
                .setFrameworkId(Devolver.devolve(frameworkInfo.getId()))
                .setAccept(org.apache.mesos.scheduler.Protos.Call.Accept.newBuilder()
                        .addOfferIds(offerId)
                        .addOperations(org.apache.mesos.Protos.Offer.Operation.newBuilder()
                                .setType(org.apache.mesos.Protos.Offer.Operation.Type.LAUNCH)
                                .setLaunch(org.apache.mesos.Protos.Offer.Operation.Launch.newBuilder()
                                        .addAllTaskInfos(tasks))
                                .build())
                        .setFilters(filters))
                .build()));

        return org.apache.mesos.Protos.Status.DRIVER_RUNNING;
    }

    @Override
    public org.apache.mesos.Protos.Status launchTasks(org.apache.mesos.Protos.OfferID offerId,
                                                      Collection<org.apache.mesos.Protos.TaskInfo> tasks) {
        mesos.send(Evolver.evolve(org.apache.mesos.scheduler.Protos.Call.newBuilder()
                .setType(org.apache.mesos.scheduler.Protos.Call.Type.ACCEPT)
                .setFrameworkId(Devolver.devolve(frameworkInfo.getId()))
                .setAccept(org.apache.mesos.scheduler.Protos.Call.Accept.newBuilder()
                        .addOfferIds(offerId)
                        .addOperations(org.apache.mesos.Protos.Offer.Operation.newBuilder()
                                .setType(org.apache.mesos.Protos.Offer.Operation.Type.LAUNCH)
                                .setLaunch(org.apache.mesos.Protos.Offer.Operation.Launch.newBuilder()
                                        .addAllTaskInfos(tasks)))
                        .build())
                .build()));

        return org.apache.mesos.Protos.Status.DRIVER_RUNNING;
    }

    @Override
    public org.apache.mesos.Protos.Status killTask(org.apache.mesos.Protos.TaskID taskId) {
        mesos.send(Evolver.evolve(org.apache.mesos.scheduler.Protos.Call.newBuilder()
                .setType(org.apache.mesos.scheduler.Protos.Call.Type.KILL)
                .setFrameworkId(Devolver.devolve(frameworkInfo.getId()))
                .setKill(org.apache.mesos.scheduler.Protos.Call.Kill.newBuilder()
                        .setTaskId(taskId)
                        .build())
                .build()));

        return org.apache.mesos.Protos.Status.DRIVER_RUNNING;
    }

    @Override
    public org.apache.mesos.Protos.Status acceptOffers(Collection<org.apache.mesos.Protos.OfferID> offerIds,
                                                       Collection<org.apache.mesos.Protos.Offer.Operation> operations,
                                                       org.apache.mesos.Protos.Filters filters) {
        mesos.send(Evolver.evolve(org.apache.mesos.scheduler.Protos.Call.newBuilder()
                .setType(org.apache.mesos.scheduler.Protos.Call.Type.ACCEPT)
                .setFrameworkId(Devolver.devolve(frameworkInfo.getId()))
                .setAccept(org.apache.mesos.scheduler.Protos.Call.Accept.newBuilder()
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
        mesos.send(Evolver.evolve(org.apache.mesos.scheduler.Protos.Call.newBuilder()
                .setType(org.apache.mesos.scheduler.Protos.Call.Type.DECLINE)
                .setFrameworkId(Devolver.devolve(frameworkInfo.getId()))
                .setDecline(org.apache.mesos.scheduler.Protos.Call.Decline.newBuilder()
                        .addOfferIds(offerId)
                        .setFilters(filters)
                        .build())
                .build()));

        return org.apache.mesos.Protos.Status.DRIVER_RUNNING;
    }

    @Override
    public org.apache.mesos.Protos.Status declineOffer(org.apache.mesos.Protos.OfferID offerId) {
        mesos.send(Evolver.evolve(org.apache.mesos.scheduler.Protos.Call.newBuilder()
                .setType(org.apache.mesos.scheduler.Protos.Call.Type.DECLINE)
                .setFrameworkId(Devolver.devolve(frameworkInfo.getId()))
                .setDecline(org.apache.mesos.scheduler.Protos.Call.Decline.newBuilder()
                        .addOfferIds(offerId)
                        .build())
                .build()));

        return org.apache.mesos.Protos.Status.DRIVER_RUNNING;
    }

    @Override
    public org.apache.mesos.Protos.Status reviveOffers() {
        mesos.send(Evolver.evolve(org.apache.mesos.scheduler.Protos.Call.newBuilder()
                .setType(org.apache.mesos.scheduler.Protos.Call.Type.REVIVE)
                .setFrameworkId(Devolver.devolve(frameworkInfo.getId()))
                .build()));

        return org.apache.mesos.Protos.Status.DRIVER_RUNNING;
    }

    @Override
    public org.apache.mesos.Protos.Status suppressOffers() {
        mesos.send(Evolver.evolve(org.apache.mesos.scheduler.Protos.Call.newBuilder()
                .setType(org.apache.mesos.scheduler.Protos.Call.Type.SUPPRESS)
                .setFrameworkId(Devolver.devolve(frameworkInfo.getId()))
                .build()));

        return org.apache.mesos.Protos.Status.DRIVER_RUNNING;
    }

    @Override
    public org.apache.mesos.Protos.Status acknowledgeStatusUpdate(org.apache.mesos.Protos.TaskStatus status) {
        mesos.send(Evolver.evolve(org.apache.mesos.scheduler.Protos.Call.newBuilder()
                .setType(org.apache.mesos.scheduler.Protos.Call.Type.ACKNOWLEDGE)
                .setFrameworkId(Devolver.devolve(frameworkInfo.getId()))
                .setAcknowledge(org.apache.mesos.scheduler.Protos.Call.Acknowledge.newBuilder()
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
        mesos.send(Evolver.evolve(org.apache.mesos.scheduler.Protos.Call.newBuilder()
                .setType(org.apache.mesos.scheduler.Protos.Call.Type.MESSAGE)
                .setFrameworkId(Devolver.devolve(frameworkInfo.getId()))
                .setMessage(org.apache.mesos.scheduler.Protos.Call.Message.newBuilder()
                        .setData(ByteString.copyFrom(data))
                        .build())
                .build()));

        return org.apache.mesos.Protos.Status.DRIVER_RUNNING;
    }

    @Override
    public org.apache.mesos.Protos.Status reconcileTasks(Collection<org.apache.mesos.Protos.TaskStatus> statuses) {
        List<org.apache.mesos.scheduler.Protos.Call.Reconcile.Task> tasks = new ArrayList<>();

        for (org.apache.mesos.Protos.TaskStatus status: statuses) {
            org.apache.mesos.scheduler.Protos.Call.Reconcile.Task.Builder builder =
                    org.apache.mesos.scheduler.Protos.Call.Reconcile.Task.newBuilder();

            builder.setTaskId(status.getTaskId());

            if (status.hasSlaveId()) {
                builder.setSlaveId(status.getSlaveId());
            }

            tasks.add(builder.build());
        }

        mesos.send(Evolver.evolve(org.apache.mesos.scheduler.Protos.Call.newBuilder()
                .setType(org.apache.mesos.scheduler.Protos.Call.Type.RECONCILE)
                .setFrameworkId(Devolver.devolve(frameworkInfo.getId()))
                .setReconcile(org.apache.mesos.scheduler.Protos.Call.Reconcile.newBuilder()
                        .addAllTasks(tasks)
                        .build())
                .build()));

        return org.apache.mesos.Protos.Status.DRIVER_RUNNING;
    }
}
