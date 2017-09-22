package com.mesosphere.sdk.scheduler;

import com.google.api.client.util.ExponentialBackOff;
import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.ByteString;
import com.mesosphere.mesos.protobuf.EvolverDevolver;
import org.apache.mesos.v1.scheduler.Mesos;
import org.apache.mesos.v1.scheduler.Protos;
import org.apache.mesos.v1.scheduler.V0Mesos;
import org.apache.mesos.v1.scheduler.V1Mesos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * SchedulerDriver-like class that uses the Mesos V1 API.
 */
public class V1SchedulerDriverAdapter implements org.apache.mesos.v1.scheduler.Scheduler, V1SchedulerDriver {

    private static final Logger LOGGER = LoggerFactory.getLogger(V1SchedulerDriverAdapter.class);

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

    // Latch for supporting `join()`.
    private CountDownLatch latch = new CountDownLatch(1);
    private Scheduler wrappedScheduler;
    private org.apache.mesos.v1.Protos.FrameworkInfo frameworkInfo;
    private final String master;
    private final org.apache.mesos.v1.Protos.Credential credential;
    private org.apache.mesos.Protos.Status status;
    private boolean registered;
    private final boolean implicitAcknowledgements;
    private Mesos mesos;
    private volatile ScheduledExecutorService subscriberTimer;
    private State state;
    private ScheduledExecutorService heartbeatTimer;
    private Instant lastHeartbeat;
    private OptionalLong heartbeatTimeout;

    public V1SchedulerDriverAdapter(Scheduler wrappedScheduler,
                                         org.apache.mesos.Protos.FrameworkInfo frameworkInfo,
                                         String master,
                                         boolean implicitAcknowledgements) {
        this.wrappedScheduler = wrappedScheduler;
        this.frameworkInfo = EvolverDevolver.evolve(frameworkInfo);
        this.credential = null;
        this.master = master;
        this.registered = false;
        this.implicitAcknowledgements = implicitAcknowledgements;
        this.status = org.apache.mesos.Protos.Status.DRIVER_NOT_STARTED;
        this.state = State.DISCONNECTED;
    }

    public V1SchedulerDriverAdapter(Scheduler wrappedScheduler,
                                         org.apache.mesos.Protos.FrameworkInfo frameworkInfo,
                                         String master,
                                         boolean implicitAcknowledgements,
                                         org.apache.mesos.Protos.Credential credential) {
        this.wrappedScheduler = wrappedScheduler;
        this.frameworkInfo = EvolverDevolver.evolve(frameworkInfo);
        this.master = master;
        this.credential = EvolverDevolver.evolve(credential);
        this.registered = false;
        this.implicitAcknowledgements = implicitAcknowledgements;
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
        subscriberTimer.schedule(new SubscriberTask(backOff), nextBackoffMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Task that performs Subscription.
     */
    private synchronized void performReliableSubscription() {
        // If timer is not running, initialize it.
        if (subscriberTimer == null) {
            LOGGER.info("Initializing reliable subscriber");
            subscriberTimer = createTimerInternal();
            ExponentialBackOff backOff = new ExponentialBackOff.Builder()
                    .setMaxElapsedTimeMillis(Integer.MAX_VALUE /* Try forever */)
                    .setMaxIntervalMillis(MAX_BACKOFF_MS)
                    .setMultiplier(MULTIPLIER)
                    .setRandomizationFactor(0.5)
                    .setInitialIntervalMillis(SEED_BACKOFF_MS)
                    .build();
            subscriberTimer.schedule(new SubscriberTask(backOff), SEED_BACKOFF_MS, TimeUnit.MILLISECONDS);
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

        org.apache.mesos.scheduler.Protos.Event event = EvolverDevolver.devolve(v1Event);

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
                            EvolverDevolver.devolve(frameworkInfo.getId()),
                            EvolverDevolver.devolve(v1Event.getSubscribed().getMasterInfo()));
                } else {
                    wrappedScheduler.reregistered(
                            this,
                            EvolverDevolver.devolve(v1Event.getSubscribed().getMasterInfo()));
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

                // The underlying implementations do not automatically
                // send acknowledgements. IFF we have
                // 'implicitAcknowledgements' turned on, AND a UUID is
                // set then we send an ACK.
                if (implicitAcknowledgements && v1Status.hasUuid()) {
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

                // Abort the adapter once the error callback is invoked similar to
                // the native scheduler driver.
                abort();
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
    @VisibleForTesting
    protected Mesos startInternal() {
        if (credential == null) {
            return new V1Mesos(this, master);
        } else {
            return new V1Mesos(this, master, credential);
        }
    }

    private void initHeartbeatTimer(double heartbeatInterval) {
        heartbeatTimeout = OptionalLong.of(5 * (long) heartbeatInterval);
        lastHeartbeat = Instant.now();

        heartbeatTimer = createTimerInternal();
        heartbeatTimer.scheduleWithFixedDelay(
                () -> heartbeat(mesos),
                heartbeatTimeout.getAsLong(),
                heartbeatTimeout.getAsLong(),
                TimeUnit.MILLISECONDS);
    }

    /**
     * Broken out into a separate function to allow speeding up the `Timer` callbacks.
     */
    @VisibleForTesting
    protected ScheduledExecutorService createTimerInternal() {
        return Executors.newSingleThreadScheduledExecutor();
    }

    private synchronized void cancelHeartbeatTimer() {
        if (heartbeatTimer == null) {
            return;
        }

        LOGGER.info("Cancelling heartbeat timer upon disconnection");

        heartbeatTimer.shutdownNow();

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

        this.latch.countDown();

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

        this.latch.countDown();

        return status = org.apache.mesos.Protos.Status.DRIVER_ABORTED;
    }

    @Override
    public org.apache.mesos.Protos.Status join() {
        // Wait for `stop()` or `abort()` to trigger the latch.
        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        synchronized (this) {
            return status;
        }
    }

    @Override
    public org.apache.mesos.Protos.Status run() {
        org.apache.mesos.Protos.Status status = start();
        return status != org.apache.mesos.Protos.Status.DRIVER_RUNNING ? status : join();
    }

    @Override
    public synchronized org.apache.mesos.Protos.Status requestResources(
            Collection<org.apache.mesos.Protos.Request> requests) {
        if (status != org.apache.mesos.Protos.Status.DRIVER_RUNNING) {
            return status;
        }

        mesos.send(EvolverDevolver.evolve(org.apache.mesos.scheduler.Protos.Call.newBuilder()
                .setType(org.apache.mesos.scheduler.Protos.Call.Type.REQUEST)
                .setFrameworkId(EvolverDevolver.devolve(frameworkInfo.getId()))
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

        mesos.send(EvolverDevolver.evolve(org.apache.mesos.scheduler.Protos.Call.newBuilder()
                .setType(org.apache.mesos.scheduler.Protos.Call.Type.ACCEPT)
                .setFrameworkId(EvolverDevolver.devolve(frameworkInfo.getId()))
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

        mesos.send(EvolverDevolver.evolve(org.apache.mesos.scheduler.Protos.Call.newBuilder()
                .setType(org.apache.mesos.scheduler.Protos.Call.Type.ACCEPT)
                .setFrameworkId(EvolverDevolver.devolve(frameworkInfo.getId()))
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

        mesos.send(EvolverDevolver.evolve(org.apache.mesos.scheduler.Protos.Call.newBuilder()
                .setType(org.apache.mesos.scheduler.Protos.Call.Type.ACCEPT)
                .setFrameworkId(EvolverDevolver.devolve(frameworkInfo.getId()))
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

        mesos.send(EvolverDevolver.evolve(org.apache.mesos.scheduler.Protos.Call.newBuilder()
                .setType(org.apache.mesos.scheduler.Protos.Call.Type.ACCEPT)
                .setFrameworkId(EvolverDevolver.devolve(frameworkInfo.getId()))
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

        mesos.send(EvolverDevolver.evolve(org.apache.mesos.scheduler.Protos.Call.newBuilder()
                .setType(org.apache.mesos.scheduler.Protos.Call.Type.KILL)
                .setFrameworkId(EvolverDevolver.devolve(frameworkInfo.getId()))
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

        mesos.send(EvolverDevolver.evolve(org.apache.mesos.scheduler.Protos.Call.newBuilder()
                .setType(org.apache.mesos.scheduler.Protos.Call.Type.ACCEPT)
                .setFrameworkId(EvolverDevolver.devolve(frameworkInfo.getId()))
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

        mesos.send(EvolverDevolver.evolve(org.apache.mesos.scheduler.Protos.Call.newBuilder()
                .setType(org.apache.mesos.scheduler.Protos.Call.Type.DECLINE)
                .setFrameworkId(EvolverDevolver.devolve(frameworkInfo.getId()))
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

        mesos.send(EvolverDevolver.evolve(org.apache.mesos.scheduler.Protos.Call.newBuilder()
                .setType(org.apache.mesos.scheduler.Protos.Call.Type.DECLINE)
                .setFrameworkId(EvolverDevolver.devolve(frameworkInfo.getId()))
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

        mesos.send(EvolverDevolver.evolve(org.apache.mesos.scheduler.Protos.Call.newBuilder()
                .setType(org.apache.mesos.scheduler.Protos.Call.Type.REVIVE)
                .setFrameworkId(EvolverDevolver.devolve(frameworkInfo.getId()))
                .build()));

        return status;
    }

    @Override
    public synchronized org.apache.mesos.Protos.Status suppressOffers() {
        if (status != org.apache.mesos.Protos.Status.DRIVER_RUNNING) {
            return status;
        }

        mesos.send(EvolverDevolver.evolve(org.apache.mesos.scheduler.Protos.Call.newBuilder()
                .setType(org.apache.mesos.scheduler.Protos.Call.Type.SUPPRESS)
                .setFrameworkId(EvolverDevolver.devolve(frameworkInfo.getId()))
                .build()));

        return status;
    }

    @Override
    public synchronized org.apache.mesos.Protos.Status acknowledgeStatusUpdate(
            org.apache.mesos.Protos.TaskStatus statusToAck) {
        if (status != org.apache.mesos.Protos.Status.DRIVER_RUNNING) {
            return status;
        }

        mesos.send(EvolverDevolver.evolve(org.apache.mesos.scheduler.Protos.Call.newBuilder()
                .setType(org.apache.mesos.scheduler.Protos.Call.Type.ACKNOWLEDGE)
                .setFrameworkId(EvolverDevolver.devolve(frameworkInfo.getId()))
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

        mesos.send(EvolverDevolver.evolve(org.apache.mesos.scheduler.Protos.Call.newBuilder()
                .setType(org.apache.mesos.scheduler.Protos.Call.Type.MESSAGE)
                .setFrameworkId(EvolverDevolver.devolve(frameworkInfo.getId()))
                .setMessage(org.apache.mesos.scheduler.Protos.Call.Message.newBuilder()
                        .setData(ByteString.copyFrom(data))
                        .setExecutorId(executorId)
                        .setSlaveId(slaveId)
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

        mesos.send(EvolverDevolver.evolve(org.apache.mesos.scheduler.Protos.Call.newBuilder()
                .setType(org.apache.mesos.scheduler.Protos.Call.Type.RECONCILE)
                .setFrameworkId(EvolverDevolver.devolve(frameworkInfo.getId()))
                .setReconcile(org.apache.mesos.scheduler.Protos.Call.Reconcile.newBuilder()
                        .addAllTasks(tasks)
                        .build())
                .build()));

        return status;
    }

    @VisibleForTesting
    protected synchronized Instant getLastHeartbeat() {
        return lastHeartbeat;
    }

    private synchronized void cancelSubscriber() {
        if (subscriberTimer == null) {
            return;
        }

        LOGGER.info("Cancelling subscriber");

        subscriberTimer.shutdownNow();

        subscriberTimer = null;
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
    public class SubscriberTask implements Runnable {
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
