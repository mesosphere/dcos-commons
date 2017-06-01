package com.mesosphere.sdk.testutils;

import com.mesosphere.sdk.scheduler.SchedulerFlags;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.yaml.RawServiceSpecBuilder;
import com.mesosphere.sdk.specification.yaml.DefaultServiceSpecBuilder;

import java.io.File;

/**
 * Test utils for {@code {@link com.mesosphere.sdk.specification.ServiceSpec}}.
 */
public class ServiceSpecTestUtils {

    public static DefaultServiceSpec getPodInstance(String serviceSpecFileName, SchedulerFlags flags) throws Exception {
        return getPodInstance(serviceSpecFileName, new DefaultServiceSpecBuilder.FileReader(), flags);
    }

    public static DefaultServiceSpec getPodInstance(
            String serviceSpecFileName, DefaultServiceSpecBuilder.FileReader fileReader, SchedulerFlags flags)
                    throws Exception {
        ClassLoader classLoader = ServiceSpecTestUtils.class.getClassLoader();
        File file = new File(classLoader.getResource(serviceSpecFileName).getFile());
        return new DefaultServiceSpecBuilder(new RawServiceSpecBuilder(file).build(), flags)
                .setFileReader(fileReader)
                .build();
    }
}
