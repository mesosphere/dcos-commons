package org.apache.mesos.executor;

import org.apache.mesos.Executor;
import org.apache.mesos.ExecutorDriver;
import org.apache.mesos.Protos;
import org.apache.mesos.offer.TaskUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;

/**
 * An {@code Executor} implementation that supports execution of long-running tasks and supporting short-lived tasks.
 */
public class CustomExecutor implements Executor {
    private static final Logger LOGGER = LoggerFactory.getLogger(
            CustomExecutor.class);
    private static final int HEALTH_CHECK_THREAD_POOL_SIZE = 10;
    private static final ScheduledExecutorService scheduledExecutorService =
            Executors.newScheduledThreadPool(HEALTH_CHECK_THREAD_POOL_SIZE);

    private Map<Protos.TaskID, ExecutorTask> launchedTasks = new HashMap<>();
    private ExecutorService executorService;
    private ExecutorTaskFactory executorTaskFactory;
    private Protos.ExecutorInfo executorInfo;
    private Protos.FrameworkInfo frameworkInfo;
    private volatile Protos.SlaveInfo slaveInfo;

    public CustomExecutor(
            final ExecutorService executorService,
            ExecutorTaskFactory executorTaskFactory) {
        this.executorService = executorService;
        this.executorTaskFactory = executorTaskFactory;
    }

    @Override
    public void registered(ExecutorDriver driver, Protos.ExecutorInfo executorInfo, Protos.FrameworkInfo frameworkInfo,
                           Protos.SlaveInfo slaveInfo) {
        LOGGER.info("Registered executor: {}", executorInfo.getExecutorId());
        this.slaveInfo = slaveInfo;
        this.executorInfo = executorInfo;
        this.frameworkInfo = frameworkInfo;

        try {
            List<TimedExecutorTask> onRegisteredTasks = executorTaskFactory.createTimedTasks(
                    DcosTaskConstants.ON_REGISTERED_TASK,
                    executorInfo,
                    driver);
            processExecutorTasksSynchronously(onRegisteredTasks);
        } catch (ExecutorTaskException | ExecutionException | InterruptedException | TimeoutException e) {
            LOGGER.error("Tasks to be run upon registration failed. Exiting with exception: ", e);
            hardExit(ExecutorErrorCode.ON_REGISTERED_TASK_FAILURE);
        }
    }

    @Override
    public void reregistered(ExecutorDriver driver, Protos.SlaveInfo slaveInfo) {
        LOGGER.info("Re-registered executor: {}", executorInfo.getExecutorId());
        this.slaveInfo = slaveInfo;

        try {
            List<TimedExecutorTask> onReregisteredTasks = executorTaskFactory.createTimedTasks(
                    DcosTaskConstants.ON_REREGISTERED_TASK,
                    executorInfo,
                    driver);
            processExecutorTasksSynchronously(onReregisteredTasks);
        } catch (ExecutorTaskException | ExecutionException | InterruptedException | TimeoutException e) {
            LOGGER.error("Tasks to be run upon re-registration failed. Exiting with exception: ", e);
            hardExit(ExecutorErrorCode.ON_REREGISTERED_TASK_FAILURE);
        }
    }

    @Override
    public void disconnected(ExecutorDriver driver) {
        LOGGER.info("Executor disconnected");
    }

    @Override
    public void launchTask(final ExecutorDriver driver, final Protos.TaskInfo task) {
        LOGGER.info("Launching task: {}", task);

        try {
            if (!task.hasData()) {
                throw new ExecutorTaskException("Task data is not specified. Nothing to do.");
            }

            final Protos.CommandInfo taskData = Protos.CommandInfo.parseFrom(task.getData());
            final Protos.Environment environment = taskData.getEnvironment();
            final Map<String, String> taskEnv = TaskUtils.fromEnvironmentToMap(environment);

            if (!taskEnv.containsKey(DcosTaskConstants.TASK_TYPE)) {
                throw new ExecutorTaskException("Unable to determine task type: " + taskEnv);
            }

            final String taskType = taskEnv.get(DcosTaskConstants.TASK_TYPE);
            final ExecutorTask taskToExecute = executorTaskFactory.createTask(taskType, task, driver);
            executorService.submit(taskToExecute);
            executeHealthCheck(task, taskToExecute);
            launchedTasks.put(task.getTaskId(), taskToExecute);
        } catch (Throwable t) {
            LOGGER.error("Error launching task = {}. Reason: {}", task, t);

            TaskUtils.sendStatus(
                    driver,
                    Protos.TaskState.TASK_FAILED,
                    task.getTaskId(),
                    task.getSlaveId(),
                    task.getExecutor().getExecutorId(),
                    String.format("Exception launching task %s",
                            t.getMessage()));
        }
    }

    private void executeHealthCheck(Protos.TaskInfo taskInfo, ExecutorTask executorTask) {
        if (!taskInfo.hasHealthCheck()) {
            LOGGER.info("No health check for task: " + taskInfo.getName());
            return;
        }

        try {
            HealthCheckMonitor healthCheckMonitor =
                    new HealthCheckMonitor(
                            HealthCheckHandler.create(
                                    taskInfo,
                                    scheduledExecutorService,
                                    new HealthCheckStats(taskInfo.getName())),
                            executorTask);
            LOGGER.info("Submitting health check monitor.");
            Future<Optional<HealthCheckStats>> futureOptionalHealthCheckStats =
                    executorService.submit(healthCheckMonitor);

            executorService.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        Optional<HealthCheckStats> optionalHealthCheckStats =
                                futureOptionalHealthCheckStats.get();
                        if (optionalHealthCheckStats.isPresent()) {
                            LOGGER.error("Health check exited with statistics: " + optionalHealthCheckStats.get());
                        } else {
                            LOGGER.error("Health check exited without statistics.");
                        }
                    } catch (InterruptedException | ExecutionException e) {
                        LOGGER.error("Failed to get health check stats with exception: ", e);
                    }
                }
            });
        } catch (HealthCheckHandler.HealthCheckValidationException ex) {
            LOGGER.error("Task did not generate a health check with exception: ", ex);
        }
    }

    @Override
    public void killTask(ExecutorDriver driver, Protos.TaskID taskId) {
        // TODO(mohit): Implement SIGKILL shutdown. Currently only perform SIGTERM.
        try {
            if (!launchedTasks.containsKey(taskId)) {
                LOGGER.info("Unknown TaskId = {}. Cannot destroy something that is unknown.", taskId);
                return;
            }
            LOGGER.info("Stopping task as part of killTask: {}", taskId);
            launchedTasks.get(taskId).stop();
        } catch (Throwable t) {
            LOGGER.error("Error killing task {}. Reason: {}", taskId, t);
        }
    }

    @Override
    public void frameworkMessage(ExecutorDriver driver, byte[] data) {
        LOGGER.error("Received framework message. But, this is not implemented.");
    }

    @Override
    public void shutdown(ExecutorDriver driver) {
        LOGGER.info("Shutting down now.");

        // Shutdown all tasks
        for (Map.Entry<Protos.TaskID, ExecutorTask> entry : launchedTasks.entrySet()) {
            final Protos.TaskID taskId = entry.getKey();
            final ExecutorTask task = entry.getValue();
            try {
                LOGGER.info("Stopping task as part of executor shutdown: {}", taskId);
                task.stop();
            } catch (Throwable t) {
                LOGGER.error("Error killing task {}. Reason: {}", taskId, t);
            }
        }
    }

    @Override
    public void error(ExecutorDriver driver, String message) {
        LOGGER.error("Error {}", message);
    }

    public Optional<Protos.SlaveInfo> getSlaveInfo() {
        return Optional.ofNullable(slaveInfo);
    }

    public void setSlaveInfo(Protos.SlaveInfo slaveInfo) {
        this.slaveInfo = slaveInfo;
    }

    public Optional<Protos.FrameworkInfo> getFrameworkInfo() {
        return Optional.ofNullable(frameworkInfo);
    }

    public void setFrameworkInfo(Protos.FrameworkInfo frameworkInfo) {
        this.frameworkInfo = frameworkInfo;
    }

    public Optional<Protos.ExecutorInfo> getExecutorInfo() {
        return Optional.ofNullable(executorInfo);
    }

    public void setExecutorInfo(Protos.ExecutorInfo executorInfo) {
        this.executorInfo = executorInfo;
    }

    public Optional<ExecutorTaskFactory> getExecutorTaskFactory() {
        return Optional.ofNullable(executorTaskFactory);
    }

    public void setExecutorTaskFactory(ExecutorTaskFactory executorTaskFactory) {
        this.executorTaskFactory = executorTaskFactory;
    }

    public Optional<ExecutorService> getExecutorService() {
        return Optional.ofNullable(executorService);
    }

    public void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }

    private void processExecutorTasksSynchronously(List<TimedExecutorTask> tasks)
            throws ExecutionException, InterruptedException, TimeoutException {
        for (TimedExecutorTask task : tasks) {
            executorService.submit(task).get(task.getTimeout().getSeconds(), TimeUnit.SECONDS);
        }
    }

    @edu.umd.cs.findbugs.annotations.SuppressWarnings("DM_EXIT")
    private void hardExit(ExecutorErrorCode errorCode) {
        System.exit(errorCode.ordinal());
    }
}
