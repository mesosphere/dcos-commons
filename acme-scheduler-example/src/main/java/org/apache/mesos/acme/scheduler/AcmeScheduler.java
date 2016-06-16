package org.apache.mesos.acme.scheduler;

import io.dropwizard.setup.Environment;
import org.apache.mesos.Protos.ExecutorID;
import org.apache.mesos.Protos.FrameworkID;
import org.apache.mesos.Protos.MasterInfo;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.OfferID;
import org.apache.mesos.Protos.SlaveID;
import org.apache.mesos.Protos.TaskStatus;
import org.apache.mesos.Scheduler;
import org.apache.mesos.SchedulerDriver;
import org.apache.mesos.acme.config.AcmeSchedulerConfiguration;
import org.apache.mesos.acme.offer.PersistentOperationRecorder;
import org.apache.mesos.acme.state.AcmeStateService;
import org.apache.mesos.acme.state.AcmeStateServiceFactory;
import org.apache.mesos.offer.LogOperationRecorder;
import org.apache.mesos.offer.OfferAccepter;
import org.apache.mesos.reconciliation.DefaultReconciler;
import org.apache.mesos.reconciliation.Reconciler;
import org.apache.mesos.scheduler.plan.Block;
import org.apache.mesos.scheduler.plan.DefaultStage;
import org.apache.mesos.scheduler.plan.DefaultStageManager;
import org.apache.mesos.scheduler.plan.DefaultStageScheduler;
import org.apache.mesos.scheduler.plan.DefaultStrategyFactory;
import org.apache.mesos.scheduler.plan.Phase;
import org.apache.mesos.scheduler.plan.ReconciliationPhase;
import org.apache.mesos.scheduler.plan.Stage;
import org.apache.mesos.scheduler.plan.StageManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Observable;

/**
 */
public class AcmeScheduler extends Observable implements Scheduler, Runnable {
  private static final Logger logger = LoggerFactory.getLogger(AcmeScheduler.class);

  //private Environment environment;//TODO(nick): fixbugs
  //private AcmeSchedulerConfiguration configuration;//TODO(nick): fixbugs

  private final DefaultStageScheduler stageScheduler;  // from dcos-commons
  private final AcmeRepairScheduler repairScheduler;
  private final OfferAccepter offerAccepter;           // from dcos-commons
  private final Reconciler reconciler;                 // from dcos-commons
  private final DefaultStageManager stageManager;      // from dcos-commons
  private final AcmeStateService acmeState;

  public AcmeScheduler(AcmeSchedulerConfiguration configuration, Environment environment) {
    //this.environment = environment;
    //this.configuration = configuration;

    /**
     * This section defines the setup of the stage plan
     */
    // this would store errors discovered in config validation step (which is missing from this example.
    List<String> stageErrors = new ArrayList<>();

    // 1.  create a reconciler
    reconciler = new DefaultReconciler();  // from dcos-commons

    // 2.  the system depends on storing task info into a store. we use zk.
    acmeState = new AcmeStateServiceFactory().getStateService();

    // 3. the state object will update task status updates
    // this is an important part of the stage life-cycle. This is where task status are updated in the store.
    // it also is used to determine what needs repair.
    addObserver(acmeState);

    // 4. you need an offer acceptor created with objects that respond to the acceptance of an offer.
    offerAccepter =
      new OfferAccepter(Arrays.asList(// from dcos-commons
        new LogOperationRecorder(), // from dcos-commons
        new PersistentOperationRecorder(acmeState)));

    // 5. create an OfferRequirementProvider which is a set of functions that
    // all return an OfferRequirement. (for create, update and repair situations)
    // created by acme and follows the OfferRequirement Pattern:
    /*AcmeOfferRequirementProvider offerRequirementProvider =
      new SandboxOfferRequirementProvider(acmeState);*///TODO(nick): fixbugs

    // 6. create a list of phases (this example uses the provided reconciliation phase)
    List<Phase> phases = Arrays.asList(
      ReconciliationPhase.create(reconciler, acmeState));  // add more Phases of blocks here!

    // 7. create a stage
    Stage stage = stageErrors.isEmpty()
      ? DefaultStage.fromList(phases)
      : DefaultStage.withErrors(phases, stageErrors);

    // 8. create a stage manager
    stageManager = new DefaultStageManager(stage, new DefaultStrategyFactory());  // from dcos-commons
    // 9. register as a listener to task status events.
    addObserver(stageManager);

    // 10. create the stageScheduler
    stageScheduler = new DefaultStageScheduler(offerAccepter);  // from dcos-commons

    // 11. and the repairScheduler
    repairScheduler = new AcmeRepairScheduler();  // acme created.
  }

  @Override
  public void run() {

  }

  @Override
  public void registered(SchedulerDriver driver, FrameworkID frameworkId, MasterInfo masterInfo) {

  }

  @Override
  public void reregistered(SchedulerDriver driver, MasterInfo masterInfo) {

  }

  @Override
  public void resourceOffers(SchedulerDriver driver, List<Offer> offers) {
    reconciler.reconcile(driver);

    List<OfferID> acceptedOffers = new ArrayList<OfferID>();

    if (reconciler.isReconciled()) {
      // get the current block, which checks status
      Block block = stageManager.getCurrentBlock();
      // see if the block wants to be scheduled
      acceptedOffers = stageScheduler.resourceOffers(driver, offers, block);
      List<Offer> unacceptedOffers = filterAcceptedOffers(offers, acceptedOffers);
      acceptedOffers.addAll(repairScheduler.resourceOffers(driver, unacceptedOffers, block));
    }

    declineOffers(driver, acceptedOffers, offers);
  }

  @Override
  public void offerRescinded(SchedulerDriver driver, OfferID offerId) {

  }

  @Override
  public void statusUpdate(SchedulerDriver driver, TaskStatus status) {
    logger.info("Received status update for taskId={} state={} message='{}'",
      status.getTaskId().getValue(), status.getState().toString(), status.getMessage());

    setChanged();
    notifyObservers(status);        // notifies blocks and state objects to record
  }

  @Override
  public void frameworkMessage(SchedulerDriver driver, ExecutorID executorId, SlaveID slaveId, byte[] data) {

  }

  @Override
  public void disconnected(SchedulerDriver driver) {

  }

  @Override
  public void slaveLost(SchedulerDriver driver, SlaveID slaveId) {

  }

  @Override
  public void executorLost(SchedulerDriver driver, ExecutorID executorId, SlaveID slaveId, int status) {

  }

  @Override
  public void error(SchedulerDriver driver, String message) {

  }

  public AcmeStateService getAcmeState() {
    return null;
  }

  public StageManager getStageManager() {
    return null;
  }

  private void declineOffers(SchedulerDriver driver, List<OfferID> acceptedOffers, List<Offer> offers) {
    for (Offer offer : offers) {
      if (!acceptedOffers.contains(offer.getId())) {
        declineOffer(driver, offer);
      }
    }
  }

  private void declineOffer(SchedulerDriver driver, Offer offer) {
    OfferID offerId = offer.getId();
    logger.info("Scheduler declining offer: {}", offerId);
    driver.declineOffer(offerId);
  }

  private List<Offer> filterAcceptedOffers(List<Offer> offers, List<OfferID> acceptedOfferIds) {
    List<Offer> filteredOffers = new ArrayList<Offer>();

    for (Offer offer : offers) {
      if (!offerAccepted(offer, acceptedOfferIds)) {
        filteredOffers.add(offer);
      }
    }

    return filteredOffers;
  }

  private boolean offerAccepted(Offer offer, List<OfferID> acceptedOfferIds) {
    for (OfferID acceptedOfferId : acceptedOfferIds) {
      if (acceptedOfferId.equals(offer.getId())) {
        return true;
      }
    }

    return false;
  }
}
