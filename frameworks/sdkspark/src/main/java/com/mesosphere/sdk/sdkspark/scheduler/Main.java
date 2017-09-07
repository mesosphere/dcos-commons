package com.mesosphere.sdk.sdkspark.scheduler;

import com.mesosphere.sdk.scheduler.SchedulerFlags;
import com.mesosphere.sdk.specification.*;

import java.io.File;

/**
 * Spark-SDK driver.
 */
public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            new SparkScheduler(new File(args[0]), SchedulerFlags.fromEnv()).run();
        }
    }
}
