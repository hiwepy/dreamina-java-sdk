package io.github.hiwepy.dreamina.cli;

import io.github.hiwepy.dreamina.DreaminaCliProperties;
import io.github.hiwepy.dreamina.cli.support.MockDreaminaCli;
import io.github.hiwepy.dreamina.exception.DreaminaCliException;
import io.github.hiwepy.dreamina.exception.DreaminaCliExecutableFailureException;
import io.github.hiwepy.dreamina.exception.DreaminaCliNonZeroExitException;
import io.github.hiwepy.dreamina.exception.DreaminaCliTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DreaminaCliExecutorFailureMockTest {

    private MockDreaminaCli mockCli;
    private DreaminaCliExecutor executor;

    @BeforeEach
    void setUp() throws Exception {
        mockCli = MockDreaminaCli.install();
        mockCli.resetLog();
        executor = mockCli.newExecutor();
    }

    @Test void invokeBlankSubcommandShouldFailValidation() {
        assertThrows(IllegalArgumentException.class, () -> executor.invoke("  ", null));
    }

    @Test void helpBlankSubcommandShouldFailValidation() {
        assertThrows(IllegalArgumentException.class, () -> executor.help(" "));
    }

    @Test void sessionRenameBlankArgsShouldFailValidation() {
        assertThrows(IllegalArgumentException.class, () -> executor.sessionRename("", "name"));
        assertThrows(IllegalArgumentException.class, () -> executor.sessionRename("1", ""));
    }

    @Test void sessionDeleteBlankIdShouldFailValidation() {
        assertThrows(IllegalArgumentException.class, () -> executor.sessionDelete(" "));
    }

    @Test void nonZeroExitShouldThrowNonZeroExitException() {
        DreaminaCliNonZeroExitException ex = assertThrows(
            DreaminaCliNonZeroExitException.class, () -> executor.invoke("__exit_nonzero", null));
        assertTrue(ex.getPartialResult().getExitCode() == 7);
    }

    @Test void timeoutShouldThrowTimeoutException() {
        DreaminaCliExecutor shortTimeout = mockCli.newExecutorWithTimeout(500L);
        assertThrows(DreaminaCliTimeoutException.class, () -> shortTimeout.invoke("__sleep_forever", null));
    }

    @Test void invalidWorkingDirectoryShouldThrowExecutableFailure() throws Exception {
        Path missing = Files.createTempDirectory("dreamina-missing-wd-");
        Files.delete(missing);
        DreaminaCliProperties props = new DreaminaCliProperties();
        props.setExecutable(mockCli.scriptPath().toAbsolutePath().toString());
        props.setWorkingDirectory(missing.toAbsolutePath().toString());
        props.setCommandTimeoutMillis(5_000L);
        assertThrows(DreaminaCliException.class, new DreaminaCliExecutor(props)::version);
    }

    @Test void missingExecutableShouldThrowCliException() {
        DreaminaCliProperties props = new DreaminaCliProperties();
        props.setExecutable("/nonexistent/dreamina-mock-missing");
        props.setCommandTimeoutMillis(5_000L);
        assertThrows(DreaminaCliException.class, new DreaminaCliExecutor(props)::version);
    }

    @Test void nonPositiveTimeoutShouldThrowIllegalState() {
        DreaminaCliProperties props = new DreaminaCliProperties();
        props.setExecutable(mockCli.scriptPath().toAbsolutePath().toString());
        props.setCommandTimeoutMillis(0L);
        assertThrows(IllegalStateException.class, new DreaminaCliExecutor(props)::version);
    }

    @Test void invalidWorkingDirectoryShouldThrowExecutableFailureException() throws Exception {
        Path missing = Files.createTempDirectory("dreamina-missing-wd-ex-");
        Files.delete(missing);
        DreaminaCliProperties props = new DreaminaCliProperties();
        props.setExecutable(mockCli.scriptPath().toAbsolutePath().toString());
        props.setWorkingDirectory(missing.toAbsolutePath().toString());
        props.setCommandTimeoutMillis(5_000L);
        assertThrows(
            DreaminaCliExecutableFailureException.class, new DreaminaCliExecutor(props)::version);
    }
}
