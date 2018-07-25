package com.mesosphere.sdk.config.validate;

import com.mesosphere.sdk.specification.DefaultSecretSpec;
import org.junit.Test;

public class VerifySecretFilePathTest {

    private static final String SECRET_PATH = "test/secret";
    private static final String ENV_KEY = "TEST_ENV";

    /* Exception.class is generic (in case we add/change fields)
           If Pattern is used in DefaultVolumeSpec,  exception is ValidationException
           If not using Pattern,  exception is ConstraintViolationException
    */
    @Test
    public void testFilePathEmpty() {
        DefaultSecretSpec.newBuilder()
                .secretPath(SECRET_PATH)
                .envKey(ENV_KEY)
                .filePath("")
                .build();
    }

    @Test(expected = Exception.class)
    public void testFilePathBlank()  {
        DefaultSecretSpec.newBuilder()
                .secretPath(SECRET_PATH)
                .envKey(ENV_KEY)
                .filePath(" ")
                .build();
    }

    @Test(expected = Exception.class)
    public void testFilePathSlash()  {
        DefaultSecretSpec.newBuilder()
                .secretPath(SECRET_PATH)
                .envKey(ENV_KEY)
                .filePath("/path/to/file")
                .build();
    }

    @Test(expected = Exception.class)
    public void testFilePathChar() {
        DefaultSecretSpec.newBuilder()
                .secretPath(SECRET_PATH)
                .envKey(ENV_KEY)
                .filePath("@?test")
                .build();
    }

    @Test(expected = Exception.class)
    public void testFilePathBeg() {
        DefaultSecretSpec.newBuilder()
                .secretPath(SECRET_PATH)
                .envKey(ENV_KEY)
                .filePath("-test")
                .build();
    }

    @Test
    public void testFilePathBegDot() {
        DefaultSecretSpec.newBuilder()
                .secretPath(SECRET_PATH)
                .envKey(ENV_KEY)
                .filePath(".test")
                .build();
    }

    @Test
    public void testFilePathDot() {
        DefaultSecretSpec.newBuilder()
                .secretPath(SECRET_PATH)
                .envKey(ENV_KEY)
                .filePath("somePath/someFile.test")
                .build();
    }

    @Test
    public void testFilePathString() {
        DefaultSecretSpec.newBuilder()
                .secretPath(SECRET_PATH)
                .envKey(ENV_KEY)
                .filePath("file")
                .build();
    }

    @Test
    public void testFilePathLong() {
        DefaultSecretSpec.newBuilder()
                .secretPath(SECRET_PATH)
                .envKey(ENV_KEY)
                .filePath("file-0/file1/file-2/file3/file_4")
                .build();
    }

    @Test
    public void testSecretPathLong() {
        DefaultSecretSpec.newBuilder()
                .secretPath("file-0/file1/file-2/file3/file_4")
                .envKey(ENV_KEY)
                .filePath("file")
                .build();
    }

    @Test
    public void testSecretPathString() {
        DefaultSecretSpec.newBuilder()
                .secretPath("file")
                .envKey(ENV_KEY)
                .filePath("file")
                .build();
    }

    @Test
    public void testEnvKeyEmpty() {
        DefaultSecretSpec.newBuilder()
                .secretPath("file")
                .envKey("")
                .filePath("file")
                .build();
    }
}
