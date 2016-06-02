package org.apache.mesos.acme.scheduler;

import io.dropwizard.Application;
import io.dropwizard.configuration.EnvironmentVariableLookup;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.java8.Java8Bundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.apache.commons.lang3.text.StrSubstitutor;
import org.apache.mesos.acme.config.DropwizardConfiguration;
import org.apache.mesos.scheduler.plan.api.StageResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;


/**
 * Main entry point for the Scheduler.
 */
public final class Main extends Application<DropwizardConfiguration> {
  private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

  public static void main(String[] args) throws Exception {
    new Main().run(args);
  }

  protected Main() {
    super();
  }

  ExecutorService acmeSchedulerExecutorService = null;

  @Override
  public String getName() {
    return "DCOS Acme Service";
  }

  @Override
  public void initialize(Bootstrap<DropwizardConfiguration> bootstrap) {
    super.initialize(bootstrap);

    StrSubstitutor strSubstitutor = new StrSubstitutor(new EnvironmentVariableLookup(false));
    strSubstitutor.setEnableSubstitutionInVariables(true);

    bootstrap.addBundle(new Java8Bundle());
    bootstrap.setConfigurationSourceProvider(
      new SubstitutingSourceProvider(
        bootstrap.getConfigurationSourceProvider(),
        strSubstitutor));
  }

  @Override
  public void run(DropwizardConfiguration configuration, Environment environment) throws Exception {
    LOGGER.info("" + configuration);

    final AcmeScheduler acmeScheduler = new AcmeScheduler(configuration.getSchedulerConfiguration(), environment);

    registerJerseyResources(acmeScheduler, environment, configuration);

    acmeSchedulerExecutorService = environment.lifecycle().
      executorService("AcmeScheduler")
      .minThreads(1)
      .maxThreads(2)
      .build();
    acmeSchedulerExecutorService.submit(acmeScheduler);
  }

  public void registerJerseyResources(
    AcmeScheduler acmeScheduler,
    Environment environment,
    DropwizardConfiguration configuration) {
    //final AcmeStateService acmeState = acmeScheduler.getAcmeState();//TODO(nick): fixbugs
    // from dcos-commons provides the /v1/plan/ web interface
    environment.jersey().register(new StageResource(acmeScheduler.getStageManager()));
  }
}
