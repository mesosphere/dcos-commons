package com.mesosphere.sdk.config.validate;

import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.testutils.TestConstants;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;

public class TLSRequiresServiceAccountTest {

    @Mock
    private PodSpec podWithTLS;
    @Mock
    private TaskSpec taskWithTLS;

    @Mock
    private PodSpec podWithoutTLS;
    @Mock
    private TaskSpec taskWithoutTLS;

    @Mock
    private SchedulerConfig schedulerConfig;

    private Optional<ServiceSpec> original = Optional.empty();

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);

        when(taskWithTLS.getTransportEncryption()).thenReturn(
                Arrays.asList(
                        new DefaultTransportEncryptionSpec.Builder()
                                .name("server")
                                .type(TransportEncryptionSpec.Type.TLS).build())
        );
        when(podWithTLS.getTasks()).thenReturn(Arrays.asList(taskWithTLS));
        when(podWithTLS.getType()).thenReturn(TestConstants.POD_TYPE);

        when(taskWithoutTLS.getTransportEncryption()).thenReturn(Collections.emptyList());
        when(podWithoutTLS.getTasks()).thenReturn(Arrays.asList(taskWithoutTLS));
        when(podWithoutTLS.getType()).thenReturn(TestConstants.POD_TYPE);
    }

    @Test
    public void testNoTLSNoServiceAccount() throws Exception {
        Collection<ConfigValidationError> errors = new TLSRequiresServiceAccount(schedulerConfig)
                .validate(original, createServiceSpec(podWithoutTLS));
        assertThat(errors, is(empty()));
        verify(schedulerConfig, times(0)).getDcosAuthTokenProvider();
    }

    @Test
    public void testNoTLSWithServiceAccount() throws Exception {
        when(schedulerConfig.getDcosAuthTokenProvider()).thenReturn(null); // if it doesn't throw, then it passes
        Collection<ConfigValidationError> errors = new TLSRequiresServiceAccount(schedulerConfig)
                .validate(original, createServiceSpec(podWithoutTLS));
        assertThat(errors, is(empty()));
        verify(schedulerConfig, times(0)).getDcosAuthTokenProvider();
    }

    @Test
    public void testWithTLSNoServiceAccount() throws Exception {
        when(schedulerConfig.getDcosAuthTokenProvider()).thenThrow(new IllegalStateException("boo"));
        Collection<ConfigValidationError> errors = new TLSRequiresServiceAccount(schedulerConfig)
                .validate(original, createServiceSpec(podWithTLS));
        assertThat(errors, hasSize(1));
        verify(schedulerConfig, times(1)).getDcosAuthTokenProvider();
    }

    @Test
    public void testTLSWithServiceAccount() throws Exception {
        when(schedulerConfig.getDcosAuthTokenProvider()).thenReturn(null); // if it doesn't throw, then it passes
        Collection<ConfigValidationError> errors = new TLSRequiresServiceAccount(schedulerConfig)
                .validate(original, createServiceSpec(podWithTLS));
        assertThat(errors, is(empty()));
        verify(schedulerConfig, times(1)).getDcosAuthTokenProvider();
    }

    @Test
    public void testNullConfigInvalid() {
        // TODO(elezar): How do we guarantee that the constructor is never called with a null value?
        //               Is a @NonNull annotation sufficient?
        Collection<ConfigValidationError> errors = new TLSRequiresServiceAccount(null)
                .validate(original, createServiceSpec(podWithTLS));
        assertThat(errors, hasSize(1));
    }

    private static ServiceSpec createServiceSpec(PodSpec podSpec) {
        return DefaultServiceSpec.newBuilder()
                .addPod(podSpec)
                .name(TestConstants.SERVICE_NAME)
                .principal(TestConstants.PRINCIPAL)
                .build();
    }
}
