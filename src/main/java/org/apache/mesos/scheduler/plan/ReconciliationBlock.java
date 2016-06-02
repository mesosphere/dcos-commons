package org.apache.mesos.scheduler.plan;

import java.util.UUID;

import org.apache.mesos.Protos;
import org.apache.mesos.offer.OfferRequirement;
import org.apache.mesos.reconciliation.Reconciler;
import org.apache.mesos.reconciliation.TaskStatusProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Block that implements Reconciliation between the Mesos Master and a
 * framework. The Block will be complete when it receives status for all
 * known tasks and then performs implicit reconciliation.
 */
public class ReconciliationBlock implements Block {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Reconciler reconciler;
    private final TaskStatusProvider taskProvider;
    private final UUID id = UUID.randomUUID();
    private Status status = Status.Pending;

    /**
     * Factory method.
     * @param reconciler The reconciler to use for reconciliation.
     * @return A new ReconciliationBlock
     */
    public static final ReconciliationBlock create(Reconciler reconciler, TaskStatusProvider taskProvider){
        return new ReconciliationBlock(reconciler, taskProvider);
    }

    private ReconciliationBlock(final Reconciler reconciler, final TaskStatusProvider taskProvider){
        this.reconciler = reconciler;
        this.taskProvider = taskProvider;
    }

    @Override
    public synchronized Status getStatus() {
      if (status == Status.Pending) {
        return status;
      }

      setStatus(reconciler.isReconciled() ? Status.Complete : Status.InProgress);
      return status;
    }

    @Override
    public synchronized void setStatus(Status newStatus) {
      status = newStatus;
    }

    @Override
    public boolean isPending() {
      return getStatus() == Status.Pending;
    }

    @Override
    public boolean isInProgress() {
      return getStatus() == Status.InProgress;
    }

    @Override
    public boolean isComplete() {
      return getStatus() == Status.Complete;
    }

    @Override
    public OfferRequirement start() {
      try {
        reconciler.start(taskProvider.getTaskStatuses());
        setStatus(Status.InProgress);
      } catch (Exception ex) {
        setStatus(Status.Pending);
        logger.error("Failed to retrieve TaskStatus Set to proceed with reconciliation.");
      }

      return null;
    }

    @Override
    public void update(Protos.TaskStatus status) {
        reconciler.update(status);
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public String getName() {
        return "Reconciliation";
    }

    @Override
    public String getMessage(){
        return isComplete() ?
                "Reconciliation complete" :
                "Reconciliation in progress unreconciled tasks =  " +
                        reconciler.remaining();
    }


    /**
     * @return The reconciler used for reconciliation.
     */
    public Reconciler getReconciler(){
        return reconciler;
    }
}
