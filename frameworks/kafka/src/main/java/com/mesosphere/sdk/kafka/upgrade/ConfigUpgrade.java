package com.mesosphere.sdk.kafka.upgrade;

import com.mesosphere.sdk.config.ConfigStore;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.state.StateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Overwrite existing ConfigState.
 */
public class ConfigUpgrade {
    protected static final Logger LOGGER = LoggerFactory.getLogger(ConfigUpgrade.class);

    public static void update(ConfigStore configStore, StateStore stateStore) {
        // Generate a ConfigStore<SpecStore> from existing ConfigStore<KafkaSchedulerConfiguration>

        DefaultServiceSpec.newBuilder().name("kafka");
    }

    public static boolean checkUpdate(){
        if (System.getenv("CONFIG_UPGRADE") != null) {
            LOGGER.info("++++++++++++++++++++++++++++++++++++++++++++++++++++++++\n"
                     + "        Kafka Configuration Update is starting "
                     + "++++++++++++++++++++++++++++++++++++++++++++++++++++++++\n");
            return true;
        }
        return false;
    }


}
