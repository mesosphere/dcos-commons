package com.mesosphere.sdk.executor;

import org.apache.mesos.ExecutorDriver;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The main entry point for the custom executor.
 */
public class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);
    private ExecutorService executorService = null;

    public static void main(String[] args) throws Exception {
        new Main().run();
    }

    public void run() throws Exception {
        final DefaultExecutorTaskFactory defaultExecutorTaskFactory = new DefaultExecutorTaskFactory();
        final CustomExecutor customExecutor =
                new CustomExecutor(Executors.newCachedThreadPool(), defaultExecutorTaskFactory);
        final ExecutorDriver driver = new MesosExecutorDriverFactory().getDriver(customExecutor);

        executorService = Executors.newCachedThreadPool();
        executorService.submit(new RunnableExecutor(driver));
    }

    private static class RunnableExecutor implements Runnable {
        private final ExecutorDriver executorDriver;

        public RunnableExecutor(ExecutorDriver executorDriver) {
            this.executorDriver = executorDriver;
        }

        @Override
        public void run() {
            Protos.Status status = executorDriver.run();
            LOGGER.info("Driver stopped: status = {}", status);
        }
    }
}
