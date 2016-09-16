package org.apache.mesos.testing;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.TextFormat;

import org.apache.mesos.Protos;
import org.apache.mesos.Scheduler;
import org.apache.mesos.SchedulerDriver;
import org.apache.mesos.Protos.FrameworkInfo;
import org.apache.mesos.scheduler.SchedulerDriverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Test class for schedulers.
 */
public class QueuedSchedulerDriver implements SchedulerDriver {
  private static final Logger LOGGER = LoggerFactory.getLogger(QueuedSchedulerDriver.class);

  public static final SchedulerDriverFactory FACTORY = new SchedulerDriverFactory() {
    @Override
    public SchedulerDriver create(
        final Scheduler scheduler, final FrameworkInfo frameworkInfo, final String masterUrl) {
      return new QueuedSchedulerDriver();
    }
  };

  private static final class SynchronizedStatus {
    private Protos.Status status = Protos.Status.DRIVER_NOT_STARTED;
    private final Lock lock = new ReentrantLock(false);
    private final Condition condition = lock.newCondition();

    private boolean isRunning() {
      switch (this.status) {
        case DRIVER_ABORTED:
        case DRIVER_STOPPED:
        case DRIVER_NOT_STARTED:
          return false;
        default:
          return true;
      }
    }

    public Protos.Status get() {
      try {
        lock.lock();
        return status;
      } finally {
        lock.unlock();
      }
    }

    public Protos.Status set(Protos.Status status) {
      LOGGER.info("Updating status from {} to {}", this.status, status);
      try {
        lock.lock();
        this.status = status;
        switch (this.status) {
          case DRIVER_ABORTED:
          case DRIVER_STOPPED:
          case DRIVER_NOT_STARTED:
            condition.signalAll();
            return this.status;
          default:
            return this.status;
        }
      } finally {
        lock.unlock();
      }
    }

    public Protos.Status join() throws InterruptedException {
      try {
        lock.lock();
        while (!isRunning()) {
          condition.await();
        }
        return status;
      } finally {
        lock.unlock();
      }
    }
  }

  /**
   * Test offer operations.
   */
  public static final class OfferOperations {

    private final Collection<Protos.OfferID> offers;
    private final Collection<Protos.Offer.Operation> operations;

    public static OfferOperations create(
      final Collection<Protos.OfferID> offers,
      final Collection<Protos.Offer.Operation> operations) {
      return new OfferOperations(offers, operations);
    }

    private OfferOperations(
      final Collection<Protos.OfferID> offers,
      final Collection<Protos.Offer.Operation> operations) {
      this.offers = ImmutableList.copyOf(offers);
      this.operations = ImmutableList.copyOf(operations);
    }

    public Collection<Protos.OfferID> getOffers() {
      return offers;
    }

    public Collection<Protos.Offer.Operation> getOperations() {
      return operations;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }

      if (!(o instanceof OfferOperations)) {
        return false;
      }
      OfferOperations that = (OfferOperations) o;
      return Objects.equals(getOffers(), that.getOffers()) &&
        Objects.equals(getOperations(), that.getOperations());
    }

    @Override
    public int hashCode() {
      return Objects.hash(getOffers(), getOperations());
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("OfferOperations{offers=");
      sb.append(offers);
      sb.append(", operations=[\n");
      int i = 0;
      for (Protos.Offer.Operation o : operations) {
        sb.append("  ");
        sb.append(TextFormat.shortDebugString(o));
        if (i + 1 < operations.size()) {
          sb.append(',');
        }
        sb.append('\n');
        ++i;
      }
      sb.append('}');
      return sb.toString();
    }
  }

  public static final long DEFAULT_TIMEOUT_MS = 60 * 1000;
  private volatile SynchronizedStatus status = new SynchronizedStatus();
  private boolean suppressed = false;
  private final BlockingQueue<OfferOperations> operations =
    new LinkedBlockingDeque<>();
  private final BlockingQueue<Protos.OfferID> declined =
    new LinkedBlockingDeque<>();
  private final BlockingQueue<Protos.TaskID> killed =
    new LinkedBlockingDeque<>();
  private final BlockingQueue<Protos.TaskStatus> reconciling =
    new LinkedBlockingDeque<>();

  private void accept(final OfferOperations ops) {
    while (true) {
      try {
        LOGGER.info("Got Operations to accept: {}", ops);
        operations.put(ops);
        printState();
        return;
      } catch (InterruptedException ex) {
        // retry
      }
    }
  }

  private void decline(final Protos.OfferID offer) {
    while (true) {
      try {
        LOGGER.info("Got OfferID to decline: {}", offer.getValue());
        declined.put(offer);
        printState();
        return;
      } catch (InterruptedException ex) {
        // retry
      }
    }
  }

  private void kill(final Protos.TaskID task) {
    while (true) {
      try {
        LOGGER.info("Got TaskID to kill: {}", task.getValue());
        killed.put(task);
        printState();
        return;
      } catch (InterruptedException ex) {
        // retry
      }
    }
  }

  private void reconcile(final Collection<Protos.TaskStatus> tasks) {
    tasks.stream().forEach(this::reconcile);
  }

  private void reconcile(final Protos.TaskStatus status) {
    while (true) {
      try {
        LOGGER.info("Got TaskStatus to reconcile: {}", status);
        reconciling.put(status);
        printState();
        return;
      } catch (InterruptedException ex) {
        // retry
      }
    }
  }

  public void printState() {
    LOGGER.info("Driver state: status = {}, operations = {}, declined = {}, reconciling = {}",
        status.get(), operations.size(), declined.size(), reconciling.size());
  }

  @Override
  public Protos.Status start() {
    return status.set(Protos.Status.DRIVER_RUNNING);
  }

  @Override
  public Protos.Status stop(boolean failover) {
    return status.set(Protos.Status.DRIVER_STOPPED);
  }

  @Override
  public Protos.Status stop() {
    return status.set(Protos.Status.DRIVER_STOPPED);
  }

  @Override
  public Protos.Status abort() {
    return status.set(Protos.Status.DRIVER_ABORTED);
  }

  @Override
  public Protos.Status join() {
    while (true) {
      try {
        return status.join();
      } catch (InterruptedException ex) {

      }
    }
  }

  @Override
  public Protos.Status run() {
    start();
    return join();
  }

  @Override
  public Protos.Status requestResources(Collection<Protos.Request> requests) {
    return status.get();
  }

  @Override
  public Protos.Status launchTasks(Collection<Protos.OfferID> offerIds,
                                   Collection<Protos.TaskInfo> tasks,
                                   Protos.Filters filters) {
    accept(OfferOperations.create(offerIds,
      Arrays.asList(Protos.Offer.Operation.newBuilder()
        .setType(Protos.Offer.Operation.Type.LAUNCH)
        .setLaunch(Protos.Offer.Operation.Launch.newBuilder()
          .addAllTaskInfos(tasks)).build())));

    return status.get();
  }

  @Override
  public Protos.Status launchTasks(Collection<Protos.OfferID> offerIds,
                                   Collection<Protos.TaskInfo> tasks) {
    return launchTasks(offerIds, tasks, Protos.Filters.getDefaultInstance());
  }

  @Override
  public Protos.Status launchTasks(Protos.OfferID offerId,
                                   Collection<Protos.TaskInfo> tasks,
                                   Protos.Filters filters) {
    return launchTasks(Arrays.asList(offerId), tasks);
  }

  @Override
  public Protos.Status launchTasks(Protos.OfferID offerId,
                                   Collection<Protos.TaskInfo> tasks) {
    return launchTasks(offerId, tasks, Protos.Filters.getDefaultInstance());
  }

  @Override
  public Protos.Status killTask(Protos.TaskID taskId) {
    kill(taskId);
    return status.get();
  }

  @Override
  public Protos.Status acceptOffers(Collection<Protos.OfferID> offerIds,
                                    Collection<Protos.Offer.Operation> operations,
                                    Protos.Filters filters) {
    accept(OfferOperations.create(offerIds, operations));
    return status.get();
  }

  @Override
  public Protos.Status declineOffer(Protos.OfferID offerId,
                                    Protos.Filters filters) {
    return declineOffer(offerId);
  }

  @Override
  public Protos.Status declineOffer(Protos.OfferID offerId) {

    decline(offerId);
    return status.get();
  }

  @Override
  public Protos.Status reviveOffers() {
    suppressed = false;
    return status.get();
  }

  @Override
  public Protos.Status suppressOffers() {
    suppressed = true;
    return status.get();
  }

  public boolean isSuppressed() {
    return suppressed;
  }

  @Override
  public Protos.Status acknowledgeStatusUpdate(Protos.TaskStatus status) {
    return this.status.get();
  }

  @Override
  public Protos.Status sendFrameworkMessage(Protos.ExecutorID executorId,
                                            Protos.SlaveID slaveId,
                                            byte[] data) {
    return status.get();
  }

  @Override
  public Protos.Status reconcileTasks(Collection<Protos.TaskStatus> statuses) {
    reconcile(statuses);
    return status.get();
  }

  public Optional<OfferOperations> getAccepted(long timeout) {
    while (true) {
      try {
        return Optional.ofNullable(
          operations.poll(timeout, TimeUnit.MILLISECONDS));
      } catch (InterruptedException ex){

      }
    }
  }

  public Optional<OfferOperations> getAccepted() {
    return getAccepted(DEFAULT_TIMEOUT_MS);
  }

  public Collection<OfferOperations> drainAccepted() {
    List<OfferOperations> ops = new ArrayList<>();
    operations.drainTo(ops);
    LOGGER.info("Draining accepted Operations: {}", ops);
    printState();
    return ops;
  }

  public Optional<Protos.OfferID> getDeclined(long timeout) {
    while (true) {
      try {
        return Optional.ofNullable(
          declined.poll(timeout, TimeUnit.MILLISECONDS));
      } catch (InterruptedException ex) {

      }
    }
  }

  public Optional<Protos.OfferID> getDeclined() {
    return getDeclined(DEFAULT_TIMEOUT_MS);
  }

  public Collection<Protos.OfferID> drainDeclined() {
    List<Protos.OfferID> declined = new ArrayList<>();
    this.declined.drainTo(declined);
    LOGGER.info("Draining declined OfferIDs: {}", declined);
    printState();
    return declined;
  }

  public Optional<Protos.TaskID> getKilled(long timeout) {
    while (true) {
      try {
        return Optional.ofNullable(
          killed.poll(timeout, TimeUnit.MILLISECONDS));
      } catch (InterruptedException ex) {

      }
    }
  }

  public Optional<Protos.TaskID> getKilled() {
    return getKilled(DEFAULT_TIMEOUT_MS);
  }

  public Collection<Protos.TaskID> drainKilled() {
    List<Protos.TaskID> killed = new ArrayList<>();
    this.killed.drainTo(killed);
    LOGGER.info("Draining killed TaskIDs: {}", killed);
    printState();
    return killed;
  }

  public Optional<Protos.TaskStatus> getReconciling(long timeout) {
    while (true) {
      try {
        return Optional.ofNullable(
          reconciling.poll(timeout, TimeUnit.MILLISECONDS));
      } catch (InterruptedException ex) {

      }
    }
  }

  public Optional<Protos.TaskStatus> getReconciling(){
    return getReconciling(DEFAULT_TIMEOUT_MS);
  }

  public Collection<Protos.TaskStatus> drainReconciling(){
    List<Protos.TaskStatus> statuses = new ArrayList<>();
    reconciling.drainTo(statuses);
    LOGGER.info("Draining TaskStatuses to reconcile: {}", statuses);
    printState();
    return statuses;
  }
}
