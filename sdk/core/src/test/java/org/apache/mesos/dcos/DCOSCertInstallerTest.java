package org.apache.mesos.dcos;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.Set;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyDouble;
import static org.mockito.Mockito.when;

public class DCOSCertInstallerTest {
    static Set<PosixFilePermission> keytoolPermissions = new HashSet<>();

    static {
        keytoolPermissions.add(PosixFilePermission.OWNER_READ);
        keytoolPermissions.add(PosixFilePermission.OWNER_WRITE);
        keytoolPermissions.add(PosixFilePermission.OWNER_EXECUTE);
    }

    private String jrePath = "testjre";
    private String keytool = "exit 0";

    @Mock
    DCOSCertInstaller.ProcessRunner mockProcessRunner;

    @Before
    public void init() throws Exception {
        MockitoAnnotations.initMocks(this);
        cleanup();
        Files.createDirectories(Paths.get(".ssl"));
        Files.createFile(Paths.get(".ssl", "ca.crt"));
        Files.createDirectories(Paths.get(jrePath));
        Files.createDirectories(Paths.get(jrePath, "bin"));
        Files.createDirectories(Paths.get(jrePath, "lib", "security"));
        Files.createFile(Paths.get(jrePath, "lib", "security", "cacerts"));
    }

    @After
    public void cleanup() throws Exception {
        FileUtils.deleteQuietly(new File(jrePath));
        FileUtils.deleteQuietly(new File(".ssl"));
    }

    @Test
    public void testInstallCertificateIdeal() throws Exception {
        final Path path = Paths.get(jrePath, "bin", "keytool");
        Files.write(path, keytool.getBytes("UTF-8"));
        Files.setPosixFilePermissions(path, keytoolPermissions);
        when(mockProcessRunner.run(any(), anyDouble())).thenReturn(0);
        Assert.assertTrue(DCOSCertInstaller.installCertificate(jrePath, mockProcessRunner));
    }

    @Test
    public void testInstallCertificateFailureKeytoolError() throws Exception {
        final Path path = Paths.get(jrePath, "bin", "keytool");
        Files.write(path, keytool.getBytes("UTF-8"));
        Files.setPosixFilePermissions(path, keytoolPermissions);
        when(mockProcessRunner.run(any(), anyDouble())).thenReturn(1);
        Assert.assertFalse(DCOSCertInstaller.installCertificate(jrePath, mockProcessRunner));
    }

    @Test
    public void testInstallCertificateFailureNoJRE() throws Exception {
        FileUtils.deleteQuietly(new File(jrePath));
        when(mockProcessRunner.run(any(), anyDouble())).thenReturn(0);
        Assert.assertFalse(DCOSCertInstaller.installCertificate(jrePath, mockProcessRunner));
    }

    @Test
    public void testInstallCertificateFailureNoCert() throws Exception {
        FileUtils.deleteQuietly(new File(".ssl"));
        when(mockProcessRunner.run(any(), anyDouble())).thenReturn(0);
        Assert.assertFalse(DCOSCertInstaller.installCertificate(jrePath, mockProcessRunner));
    }
}
