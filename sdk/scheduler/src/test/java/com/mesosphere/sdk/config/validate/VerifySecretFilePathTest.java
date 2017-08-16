package com.mesosphere.sdk.config.validate;

import com.mesosphere.sdk.specification.DefaultSecretSpec;
import org.junit.Test;

public class VerifySecretFilePathTest {

    private static final String secretPath = "test/secret";
    private static final String envKey = "TEST_ENV";

    /* Exception.class is generic (in case we add/change fields)
           If Pattern is used in DefaultVolumeSpec,  exception is ValidationException
           If not using Pattern,  exception is ConstraintViolationException
    */
    @Test
    public void testFilePathEmpty() {
        new DefaultSecretSpec( secretPath, envKey, "");
    }

    @Test(expected = Exception.class)
    public void testFilePathBlank()  {
        new DefaultSecretSpec( secretPath, envKey, " ");
    }

    @Test(expected = Exception.class)
    public void testFilePathSlash()  {
        new DefaultSecretSpec( secretPath, envKey, "/path/to/file");
    }

    @Test(expected = Exception.class)
    public void testFilePathChar() {
        new DefaultSecretSpec( secretPath, envKey, "@?test");
    }

    @Test(expected = Exception.class)
    public void testFilePathBeg() {
        new DefaultSecretSpec( secretPath, envKey, "-test");
    }

    @Test
    public void testFilePathBegDot() {
        new DefaultSecretSpec( secretPath, envKey, ".test");
    }


    @Test
    public void testFilePathDot() {
        new DefaultSecretSpec( secretPath, envKey, "somePath/someFile.test");
    }

    @Test
    public void testFilePathString() {
        new DefaultSecretSpec( secretPath, envKey, "file");
    }

    @Test
    public void testFilePathLong() {
        new DefaultSecretSpec( secretPath, envKey, "file-0/file1/file-2/file3/file_4");
    }

    @Test
    public void testSecretPathLong() {
        new DefaultSecretSpec( "file-0/file1/file-2/file3/file_4", envKey, "file" );
    }

    @Test
    public void testSecretPathString() {
        new DefaultSecretSpec( "file", envKey, "file" );
    }

    @Test
    public void testEnvKeyEmpty() {
        new DefaultSecretSpec( "file", "", "file" );
    }


}
