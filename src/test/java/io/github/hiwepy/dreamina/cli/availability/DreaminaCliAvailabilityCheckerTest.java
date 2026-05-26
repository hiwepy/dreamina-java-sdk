package io.github.hiwepy.dreamina.cli.availability;

import io.github.hiwepy.dreamina.DreaminaCliProperties;
import io.github.hiwepy.dreamina.cli.DreaminaCliExecutor;
import io.github.hiwepy.dreamina.cli.support.MockDreaminaCli;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DreaminaCliAvailabilityChecker} 单元测试。
 */
class DreaminaCliAvailabilityCheckerTest {

    @Test
    void checkShouldSucceedWithMockExecutable() throws Exception {
        MockDreaminaCli mock = MockDreaminaCli.install();
        DreaminaCliProperties props = new DreaminaCliProperties();
        props.setExecutable(mock.scriptPath().toAbsolutePath().toString());
        props.setStartupProbeTimeoutMillis(5_000L);

        DreaminaCliAvailabilityReport report = new DreaminaCliAvailabilityChecker().check(props);

        assertTrue(report.isAvailable());
        assertEquals(DreaminaCliAvailabilityStatus.AVAILABLE, report.getStatus());
        assertTrue(report.getResolvedExecutablePath() != null && !report.getResolvedExecutablePath().isEmpty());
    }

    @Test
    void checkShouldFailWhenExecutableMissing() {
        DreaminaCliProperties props = new DreaminaCliProperties();
        props.setExecutable("/nonexistent/dreamina-mock-missing");
        props.setStartupProbeTimeoutMillis(3_000L);

        DreaminaCliAvailabilityReport report = new DreaminaCliAvailabilityChecker().check(props);

        assertFalse(report.isAvailable());
        assertEquals(DreaminaCliAvailabilityStatus.EXECUTABLE_NOT_FOUND, report.getStatus());
    }

    @Test
    void checkViaExecutorShouldMatchPropertiesProbe() throws Exception {
        MockDreaminaCli mock = MockDreaminaCli.install();
        DreaminaCliExecutor executor = mock.newExecutor();
        DreaminaCliAvailabilityReport report = new DreaminaCliAvailabilityChecker().check(executor);
        assertTrue(report.isAvailable());
    }

    @Test
    void resolveExecutablePathShouldFindMockScript() throws Exception {
        MockDreaminaCli mock = MockDreaminaCli.install();
        Path path = mock.scriptPath();
        assertTrue(DreaminaCliAvailabilityChecker.resolveExecutablePath(path.toAbsolutePath().toString()).isPresent());
        assertFalse(Files.notExists(path));
    }
}
