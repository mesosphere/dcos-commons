package com.mesosphere.sdk.config.validate;

import com.mesosphere.sdk.specification.ServiceSpec;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Optional;

public class ServiceNameCannotBreakDNSTest {

    ServiceNameCannotBreakDNS validator = new ServiceNameCannotBreakDNS();
    ServiceSpec mockNewSpec = Mockito.mock(ServiceSpec.class);
    ServiceSpec mockOldSpec = Mockito.mock(ServiceSpec.class);

    @Test
    public void serviceNameUnderLimitNewDeploy() {
        Mockito.when(mockNewSpec.getName()).thenReturn("i/am/a/short/name");
        Assert.assertEquals(0, validator.validate(Optional.empty(), mockNewSpec).size());
    }

    @Test
    public void serviceNameAtLimitNewDeploy() {
        Mockito.when(mockNewSpec.getName())
                .thenReturn("i/am/a/name/that/is/exactly/sixty/three/characters/long/no/really/do/you/not/b");
        Assert.assertEquals(0, validator.validate(Optional.empty(), mockNewSpec).size());
    }

    @Test
    public void serviceNameOverLimitNewDeploy() {
        Mockito.when(mockNewSpec.getName())
                .thenReturn("i/am/a/name/that/is/" +
                        "loooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooong");
        Assert.assertEquals(1, validator.validate(Optional.empty(), mockNewSpec).size());
    }

    @Test
    public void serviceNameOverLimitExistingDeploy() {
        Mockito.when(mockNewSpec.getName())
                .thenReturn("i/am/a/name/that/is/" +
                        "loooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooong");
        Assert.assertEquals(0, validator.validate(Optional.of(mockOldSpec), mockNewSpec).size());
    }

    @Test
    public void serviceNameUnderLimitExistingDeploy() {
        Mockito.when(mockNewSpec.getName()).thenReturn("i/am/a/short/name");
        Assert.assertEquals(0, validator.validate(Optional.of(mockOldSpec), mockNewSpec).size());
    }
}
