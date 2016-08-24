package org.apache.mesos.scheduler;

import com.google.api.client.util.ExponentialBackOff;
import com.google.protobuf.ByteString;
import org.apache.mesos.protobuf.Devolver;
import org.apache.mesos.protobuf.Evolver;
import org.apache.mesos.v1.scheduler.JNIMesos;
import org.apache.mesos.v1.scheduler.Mesos;
import org.apache.mesos.v1.scheduler.Protos;
import org.apache.mesos.v1.scheduler.V0Mesos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * This is a threadsafe  adapter from the new v1 `Mesos` + `Scheduler` interface to the old  v0 `SchedulerDriver`
 * + `Scheduler` interface. It intercepts:
 * - The v1 scheduler callbacks and converts them into appropriate v0 scheduler callbacks.
 * - The various `driver.xx()` calls, creates a `Call` message and then invokes `send()` on the v1
 * `Mesos` interface.
 */
public class MesosToSchedulerDriverAdapter implements
        org.apache.mesos.v1.scheduler.Scheduler,
        org.apache.mesos.SchedulerDriver {

    private static final Logger LOGGER = LoggerFactory.getLogger(MesosToSchedulerDriverAdapter.class);

    // State of the `Mesos` interface.
    private enum State {
        DISCONNECTED,
        CONNECTED,
        SUBSCRIBED
    }

    // Exponential back-off between consecutive subscribe calls.
    private static final int MULTIPLIER = 2;
    private static final int SEED_BACKOFF_MS = 2000;
    private static final int MAX_BACKOFF_MS = 30000;

    private org.apache.mesos.Scheduler wrappedScheduler;
    private org.apache.mesos.v1.Protos.FrameworkInfo frameworkInfo;
    private final String master;
    private final org.apache.mesos.v1.Protos.Credential credential;
    // TODO(anand): This can change to `v1.Status` once we add support for devolving enums.
    private org.apache.mesos.Protos.Status status;
    private boolean registered;
    private Mesos mesos;
    private volatile Timer subscriberTimer;
    private State state;
    private Timer heartbeatTimer;
    private Instant lastHeartbeat;
    private OptionalLong heartbeatTimeout;

    public MesosToSchedulerDriverAdapter(org.apache.mesos.Scheduler wrappedScheduler,
                                         org.apache.mesos.Protos.FrameworkInfo frameworkInfo,
                                         String master) {
        this.wrappedScheduler = wrappedScheduler;
        this.frameworkInfo = Evolver.evolve(frameworkInfo);
        this.credential = null;
        this.master = master;
        this.registered = false;
        this.status = org.apache.mesos.Protos.Status.DRIVER_NOT_STARTED;
        this.state = State.DISCONNECTED;
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
        this.status = org.apache.mesos.Protos.Status.DRIVER_NOT_STARTED;
        this.state = State.DISCONNECTED;
    }

    @Override
    public synchronized void connected(Mesos mesos) {
        if (status != org.apache.mesos.Protos.Status.DRIVER_RUNNING) {
            return;
        }

        LOGGER.info("Connected!");

        state = State.CONNECTED;

        performReliableSubscription();
    }

    private synchronized void subscribe(ExponentialBackOff backOff) throws IOException {
        if (state == State.SUBSCRIBED || state == State.DISCONNECTED) {
            LOGGER.debug("Cancelling subscriber. As we are in state: {}", state);
            cancelSubscriber();
            return;
        }
        LOGGER.info("Sending SUBSCRIBE call");

        final Protos.Call.Builder callBuilder = Protos.Call.newBuilder()
                .setType(Protos.Call.Type.SUBSCRIBE)
                .setSubscribe(Protos.Call.Subscribe.newBuilder()
                        .setFrameworkInfo(frameworkInfo)
                        .build());
        if (frameworkInfo.hasId()) {
            callBuilder.setFrameworkId(frameworkInfo.getId());
        }

        mesos.send(callBuilder.build());

        scheduleNextSubscription(backOff);
    }

    private synchronized void scheduleNextSubscription(ExponentialBackOff backOff) throws IOException {
        long nextBackoffMs;
        nextBackoffMs = backOff.nextBackOffMillis();
        LOGGER.info("Backing off for: {}", nextBackoffMs);
        subscriberTimer.schedule(new SubscriberTask(backOff), nextBackoffMs);
    }

    /**
     * Task that performs Subscription.
     */
    private synchronized void performReliableSubscription() {
        // If timer is not running, initialize it.
        if (subscriberTimer == null) {
            LOGGER.info("Initializing reliable subscriber...");
            subscriberTimer = createTimerInternal();
            ExponentialBackOff backOff = new ExponentialBackOff.Builder()
                    .setMaxElapsedTimeMillis(Integer.MAX_VALUE /* Try forever */)
                    .setMaxIntervalMillis(MAX_BACKOFF_MS)
                    .setMultiplier(MULTIPLIER)
                    .setRandomizationFactor(0.5)
                    .setInitialIntervalMillis(SEED_BACKOFF_MS)
                    .build();
            subscriberTimer.schedule(new SubscriberTask(backOff), SEED_BACKOFF_MS);
        }
    }

    @Override
    public synchronized void disconnected(Mesos mesos) {
        if (status != org.apache.mesos.Protos.Status.DRIVER_RUNNING) {
            return;
        }

        LOGGER.info("Disconnected!");

        state = State.DISCONNECTED;

        cancelSubscriber();
        cancelHeartbeatTimer();

        wrappedScheduler.disconnected(this);
    }

    @Override
    public synchronized void received(Mesos mesos, Protos.Event v1Event) {
        if (status != org.apache.mesos.Protos.Status.DRIVER_RUNNING) {
            return;
        }

        org.apache.mesos.scheduler.Protos.Event event = Devolver.devolve(v1Event);

        LOGGER.info("Received event of type: {}", event.getType());

        switch (event.getType()) {
            case SUBSCRIBED: {
                state = State.SUBSCRIBED;

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

                initHeartbeatTimer(event.getSubscribed().getHeartbeatIntervalSeconds());

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

                // Send ACK only when UUID is set.
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
                wrappedScheduler.error(this, event.getError().getMessage());
                break;
            }

            case HEARTBEAT: {
                lastHeartbeat = Instant.now();
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
    public synchronized org.apache.mesos.Protos.Status start() {
        if (status != org.apache.mesos.Protos.Status.DRIVER_NOT_STARTED) {
            return status;
        }

        this.mesos = startInternal();

        return status = org.apache.mesos.Protos.Status.DRIVER_RUNNING;
    }

    /**
     * Broken out into a separate function to allow testing with custom `Mesos` implementations.
     */
    protected Mesos startInternal() {
        String version = System.getenv("MESOS_API_VERSION");
        if (version == null) {
            version = "V0";
        }

        LOGGER.info("Using Mesos API version: {}", version);

        if (version.equals("V0")) {
            if (credential == null) {
                return new V0Mesos(this, frameworkInfo, master);
            } else {
                return new V0Mesos(this, frameworkInfo, master, credential);
            }
        } else if (version.equals("V1")) {
            if (credential == null) {
                return new JNIMesos(this, master);
            } else {
                return new JNIMesos(this, master, credential);
            }
        } else {
            throw new IllegalArgumentException("Unsupported API version: " + version);
        }
    }

    private void initHeartbeatTimer(double heartbeatInterval) {
        heartbeatTimeout = OptionalLong.of(5 * (long) heartbeatInterval);
        lastHeartbeat = Instant.now();

        heartbeatTimer = createTimerInternal();
        heartbeatTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                heartbeat(mesos);
            }
        }, heartbeatTimeout.getAsLong(), heartbeatTimeout.getAsLong());
    }

    /**
     * Broken out into a separate function to allow speeding up the `Timer` callbacks.
     */
    protected Timer createTimerInternal() {
        return new Timer();
    }

    private synchronized void cancelHeartbeatTimer() {
        LOGGER.info("Cancelling heartbeat timer upon disconnection");

        // Cancel previous heartbeat timer if one exists.
        if (heartbeatTimer != null) {
            heartbeatTimer.cancel();
            heartbeatTimer.purge();
        }

        heartbeatTimer = null;
        heartbeatTimeout = null;
        lastHeartbeat = null;
    }

    @Override
    public synchronized org.apache.mesos.Protos.Status stop(boolean failover) {
        if (status != org.apache.mesos.Protos.Status.DRIVER_RUNNING) {
            return status;
        }

        if (!failover) {
            mesos.send(org.apache.mesos.v1.scheduler.Protos.Call.newBuilder()
                    .setType(org.apache.mesos.v1.scheduler.Protos.Call.Type.TEARDOWN)
                    .setFrameworkId(frameworkInfo.getId())
                    .build());
        }

        // This should ensure that the underlying native implementation is eventually GC'ed.
        this.mesos = null;
        return status = org.apache.mesos.Protos.Status.DRIVER_STOPPED;
    }

    @Override
    public synchronized org.apache.mesos.Protos.Status stop() {
        return stop(false);
    }

    @Override
    public synchronized org.apache.mesos.Protos.Status abort() {
        if (status != org.apache.mesos.Protos.Status.DRIVER_RUNNING) {
            return status;
        }

        // This should ensure that the underlying native implementation is eventually GC'ed.
        this.mesos = null;

        return status = org.apache.mesos.Protos.Status.DRIVER_ABORTED;
    }

    @Override
    public synchronized org.apache.mesos.Protos.Status join() {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized org.apache.mesos.Protos.Status run() {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized org.apache.mesos.Protos.Status requestResources(
            Collection<org.apache.mesos.Protos.Request> requests) {
        if (status != org.apache.mesos.Protos.Status.DRIVER_RUNNING) {
            return status;
        }

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
    public synchronized org.apache.mesos.Protos.Status launchTasks(Collection<org.apache.mesos.Protos.OfferID> offerIds,
                                                                   Collection<org.apache.mesos.Protos.TaskInfo> tasks,
                                                                   org.apache.mesos.Protos.Filters filters) {
        if (status != org.apache.mesos.Protos.Status.DRIVER_RUNNING) {
            return status;
        }

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

        return status;
    }

    @Override
    public synchronized org.apache.mesos.Protos.Status launchTasks(Collection<org.apache.mesos.Protos.OfferID> offerIds,
                                                                   Collection<org.apache.mesos.Protos.TaskInfo> tasks) {
        if (status != org.apache.mesos.Protos.Status.DRIVER_RUNNING) {
            return status;
        }

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

        return status;
    }

    @Override
    public synchronized org.apache.mesos.Protos.Status launchTasks(org.apache.mesos.Protos.OfferID offerId,
                                                                   Collection<org.apache.mesos.Protos.TaskInfo> tasks,
                                                                   org.apache.mesos.Protos.Filters filters) {
        if (status != org.apache.mesos.Protos.Status.DRIVER_RUNNING) {
            return status;
        }

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

        return status;
    }

    @Override
    public synchronized org.apache.mesos.Protos.Status launchTasks(org.apache.mesos.Protos.OfferID offerId,
                                                                   Collection<org.apache.mesos.Protos.TaskInfo> tasks) {
        if (status != org.apache.mesos.Protos.Status.DRIVER_RUNNING) {
            return status;
        }

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

        return status;
    }

    @Override
    public synchronized org.apache.mesos.Protos.Status killTask(org.apache.mesos.Protos.TaskID taskId) {
        if (status != org.apache.mesos.Protos.Status.DRIVER_RUNNING) {
            return status;
        }

        mesos.send(Evolver.evolve(org.apache.mesos.scheduler.Protos.Call.newBuilder()
                .setType(org.apache.mesos.scheduler.Protos.Call.Type.KILL)
                .setFrameworkId(Devolver.devolve(frameworkInfo.getId()))
                .setKill(org.apache.mesos.scheduler.Protos.Call.Kill.newBuilder()
                        .setTaskId(taskId)
                        .build())
                .build()));

        return status;
    }

    @Override
    public synchronized org.apache.mesos.Protos.Status acceptOffers(
            Collection<org.apache.mesos.Protos.OfferID> offerIds,
            Collection<org.apache.mesos.Protos.Offer.Operation> operations,
            org.apache.mesos.Protos.Filters filters) {
        if (status != org.apache.mesos.Protos.Status.DRIVER_RUNNING) {
            return status;
        }

        mesos.send(Evolver.evolve(org.apache.mesos.scheduler.Protos.Call.newBuilder()
                .setType(org.apache.mesos.scheduler.Protos.Call.Type.ACCEPT)
                .setFrameworkId(Devolver.devolve(frameworkInfo.getId()))
                .setAccept(org.apache.mesos.scheduler.Protos.Call.Accept.newBuilder()
                        .addAllOfferIds(offerIds)
                        .addAllOperations(operations)
                        .setFilters(filters)
                        .build())
                .build()));

        return status;
    }

    @Override
    public synchronized org.apache.mesos.Protos.Status declineOffer(org.apache.mesos.Protos.OfferID offerId,
                                                                    org.apache.mesos.Protos.Filters filters) {
        if (status != org.apache.mesos.Protos.Status.DRIVER_RUNNING) {
            return status;
        }

        mesos.send(Evolver.evolve(org.apache.mesos.scheduler.Protos.Call.newBuilder()
                .setType(org.apache.mesos.scheduler.Protos.Call.Type.DECLINE)
                .setFrameworkId(Devolver.devolve(frameworkInfo.getId()))
                .setDecline(org.apache.mesos.scheduler.Protos.Call.Decline.newBuilder()
                        .addOfferIds(offerId)
                        .setFilters(filters)
                        .build())
                .build()));

        return status;
    }

    @Override
    public synchronized org.apache.mesos.Protos.Status declineOffer(org.apache.mesos.Protos.OfferID offerId) {
        if (status != org.apache.mesos.Protos.Status.DRIVER_RUNNING) {
            return status;
        }

        mesos.send(Evolver.evolve(org.apache.mesos.scheduler.Protos.Call.newBuilder()
                .setType(org.apache.mesos.scheduler.Protos.Call.Type.DECLINE)
                .setFrameworkId(Devolver.devolve(frameworkInfo.getId()))
                .setDecline(org.apache.mesos.scheduler.Protos.Call.Decline.newBuilder()
                        .addOfferIds(offerId)
                        .build())
                .build()));

        return status;
    }

    @Override
    public synchronized org.apache.mesos.Protos.Status reviveOffers() {
        if (status != org.apache.mesos.Protos.Status.DRIVER_RUNNING) {
            return status;
        }

        mesos.send(Evolver.evolve(org.apache.mesos.scheduler.Protos.Call.newBuilder()
                .setType(org.apache.mesos.scheduler.Protos.Call.Type.REVIVE)
                .setFrameworkId(Devolver.devolve(frameworkInfo.getId()))
                .build()));

        return status;
    }

    @Override
    public synchronized org.apache.mesos.Protos.Status suppressOffers() {
        if (status != org.apache.mesos.Protos.Status.DRIVER_RUNNING) {
            return status;
        }

        mesos.send(Evolver.evolve(org.apache.mesos.scheduler.Protos.Call.newBuilder()
                .setType(org.apache.mesos.scheduler.Protos.Call.Type.SUPPRESS)
                .setFrameworkId(Devolver.devolve(frameworkInfo.getId()))
                .build()));

        return status;
    }

    @Override
    public synchronized org.apache.mesos.Protos.Status acknowledgeStatusUpdate(
            org.apache.mesos.Protos.TaskStatus statusToAck) {
        if (status != org.apache.mesos.Protos.Status.DRIVER_RUNNING) {
            return status;
        }

        mesos.send(Evolver.evolve(org.apache.mesos.scheduler.Protos.Call.newBuilder()
                .setType(org.apache.mesos.scheduler.Protos.Call.Type.ACKNOWLEDGE)
                .setFrameworkId(Devolver.devolve(frameworkInfo.getId()))
                .setAcknowledge(org.apache.mesos.scheduler.Protos.Call.Acknowledge.newBuilder()
                        .setSlaveId(statusToAck.getSlaveId())
                        .setTaskId(statusToAck.getTaskId())
                        .setUuid(statusToAck.getUuid())
                        .build())
                .build()));

        return status;
    }

    @Override
    public synchronized org.apache.mesos.Protos.Status sendFrameworkMessage(
            org.apache.mesos.Protos.ExecutorID executorId,
            org.apache.mesos.Protos.SlaveID slaveId,
            byte[] data) {
        if (status != org.apache.mesos.Protos.Status.DRIVER_RUNNING) {
            return status;
        }

        mesos.send(Evolver.evolve(org.apache.mesos.scheduler.Protos.Call.newBuilder()
                .setType(org.apache.mesos.scheduler.Protos.Call.Type.MESSAGE)
                .setFrameworkId(Devolver.devolve(frameworkInfo.getId()))
                .setMessage(org.apache.mesos.scheduler.Protos.Call.Message.newBuilder()
                        .setData(ByteString.copyFrom(data))
                        .build())
                .build()));

        return status;
    }

    @Override
    public synchronized org.apache.mesos.Protos.Status reconcileTasks(
            Collection<org.apache.mesos.Protos.TaskStatus> statuses) {
        if (status != org.apache.mesos.Protos.Status.DRIVER_RUNNING) {
            return status;
        }

        List<org.apache.mesos.scheduler.Protos.Call.Reconcile.Task> tasks = new ArrayList<>();

        for (org.apache.mesos.Protos.TaskStatus status : statuses) {
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

        return status;
    }

    private void cancelSubscriber() {
        LOGGER.info("Cancelling subscriber...");
        synchronized (subscriberTimer) {
            subscriberTimer.cancel();
            subscriberTimer.purge();
            subscriberTimer = null;
        }
    }

    private synchronized void heartbeat(final Mesos mesos) {
        // Don't bother checking for heartbeats if we are not subscribed.
        if (state == State.DISCONNECTED || state == State.CONNECTED) {
            return;
        }

        Duration elapsed = Duration.between(lastHeartbeat, Instant.now());

        // Force reconnection if we have not received heartbeats.
        if (elapsed.getSeconds() >= heartbeatTimeout.getAsLong()) {
            LOGGER.info("Forcing reconnection with the master due to not receiving heartbeat events for "
                    + elapsed.getSeconds() + " seconds");

            mesos.reconnect();

            // Cancel the heartbeat timer now to prevent further reconnects. It is possible that we got partitioned
            // away from the master and are not able to reconnect. If we don't cancel the timer, we would trigger
            // reconnection again.
            cancelHeartbeatTimer();
        }
    }

    /**
     * Sends subscription call, and rescehdules next subscription attempt.
     */
    public class SubscriberTask extends TimerTask {
        ExponentialBackOff backOff;

        public SubscriberTask(ExponentialBackOff backOff) {
            this.backOff = backOff;
        }

        @Override
        public void run() {
            try {
                subscribe(backOff);
            } catch (IOException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
    }
}
