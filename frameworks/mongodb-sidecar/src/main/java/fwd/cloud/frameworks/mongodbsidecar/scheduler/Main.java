package fwd.cloud.frameworks.mongodbsidecar.scheduler;

import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import com.mesosphere.sdk.specification.yaml.YAMLServiceSpecFactory;
import com.mesosphere.sdk.api.types.RestartHook;
import com.mesosphere.sdk.config.validate.TaskVolumesCannotChange;

import java.io.File;
import java.util.Arrays;

/**
 * Main.
 */
public class Main {

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            new DefaultService(getBuilder(YAMLServiceSpecFactory.generateRawSpecFromYAML(new File(args[0]))));
        } else {
            System.exit(1);
        }
    }

    private static DefaultScheduler.Builder getBuilder(RawServiceSpec rawServiceSpec)
            throws Exception {
        RestartHook hook = new CustomHook();

        DefaultServiceSpec serviceSpec = YAMLServiceSpecFactory.generateServiceSpec(rawServiceSpec);
        DefaultScheduler.Builder builder =
                DefaultScheduler.newBuilder(serviceSpec)
                .setPlansFrom(rawServiceSpec)
                .setRestartHook(hook)
                .setConfigValidators(Arrays.asList(new TaskVolumesCannotChange()));

        return builder;
    }
}
