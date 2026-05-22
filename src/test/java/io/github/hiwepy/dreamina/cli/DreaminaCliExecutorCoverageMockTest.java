package io.github.hiwepy.dreamina.cli;

import io.github.hiwepy.dreamina.DreaminaCliProperties;
import io.github.hiwepy.dreamina.cli.parser.DreaminaParsedFields;
import io.github.hiwepy.dreamina.cli.support.MockDreaminaCli;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import io.github.hiwepy.dreamina.exception.DreaminaCliException;
import io.github.hiwepy.dreamina.exception.DreaminaCliExecutableFailureException;
import io.github.hiwepy.dreamina.exception.DreaminaCliNonZeroExitException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecuteResultHandler;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteResultHandler;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 补齐 {@link DreaminaCliExecutor} 私有分支与 {@code run()} 异常路径的 mock 覆盖。
 */
class DreaminaCliExecutorCoverageMockTest {

    private MockDreaminaCli mockCli;
    private DreaminaCliExecutor executor;

    @BeforeEach
    void setUp() throws Exception {
        mockCli = MockDreaminaCli.install();
        mockCli.resetLog();
        executor = mockCli.newExecutor();
    }

    @Test void checkLoginBlankDeviceCodeShouldFailValidation() {
        assertThrows(IllegalArgumentException.class, () -> executor.checkLogin("  ", 0));
    }

    @Test void checkLoginNegativePollShouldFailValidation() {
        assertThrows(IllegalArgumentException.class, () -> executor.checkLogin("dev", -1));
    }

    @Test void loginHeadlessNullAdditionalArgsOverload() throws Exception {
        assertTrue(executor.loginHeadless(null).isSuccess());
    }

    @Test void loginHeadlessSkipsBlankAdditionalArgs() throws Exception {
        assertTrue(executor.loginHeadless(Arrays.asList(null, "  ", "--verbose")).isSuccess());
    }

    @Test void validWorkingDirectoryShouldBeApplied() throws Exception {
        Path wd = Files.createTempDirectory("dreamina-mock-wd-");
        DreaminaCliProperties props = new DreaminaCliProperties();
        props.setExecutable(mockCli.scriptPath().toAbsolutePath().toString());
        props.setWorkingDirectory(wd.toAbsolutePath().toString());
        props.setCommandTimeoutMillis(5_000L);
        assertTrue(new DreaminaCliExecutor(props).version().isSuccess());
    }

    @Test void blankWorkingDirectoryPropertyShouldBeIgnored() throws Exception {
        DreaminaCliProperties props = new DreaminaCliProperties();
        props.setExecutable(mockCli.scriptPath().toAbsolutePath().toString());
        props.setWorkingDirectory("   ");
        props.setCommandTimeoutMillis(5_000L);
        assertTrue(new DreaminaCliExecutor(props).version().isSuccess());
    }

    @Test void exitOneWithoutExecuteExceptionShouldThrowNonZeroExit() {
        assertThrows(DreaminaCliNonZeroExitException.class, () -> executor.invoke("__exit_one", null));
    }

    @Test void completeAfterWaitGenericAsyncFailure() {
        CommandLine cmd = new CommandLine("dreamina");
        StubHandler handler = new StubHandler(0, false);
        ExecuteWatchdog watchdog = new ExecuteWatchdog(5_000L);
        assertThrows(
            DreaminaCliException.class,
            () -> executor.completeAfterWait(
                cmd,
                5_000L,
                new ByteArrayOutputStream(),
                new ByteArrayOutputStream(),
                handler,
                watchdog,
                new RuntimeException("async")));
    }

    @Test void completeAfterWaitMissingExitCode() {
        CommandLine cmd = new CommandLine("dreamina");
        StubHandler handler = new StubHandler(0, true);
        ExecuteWatchdog watchdog = new ExecuteWatchdog(5_000L);
        assertThrows(
            DreaminaCliException.class,
            () -> executor.completeAfterWait(
                cmd,
                5_000L,
                new ByteArrayOutputStream(),
                new ByteArrayOutputStream(),
                handler,
                watchdog,
                null));
    }

    @Test void completeAfterWaitPlainNonZeroExit() {
        CommandLine cmd = new CommandLine("dreamina");
        StubHandler handler = new StubHandler(1, false);
        ExecuteWatchdog watchdog = new ExecuteWatchdog(5_000L);
        assertThrows(
            DreaminaCliNonZeroExitException.class,
            () -> executor.completeAfterWait(
                cmd,
                5_000L,
                new ByteArrayOutputStream(),
                new ByteArrayOutputStream(),
                handler,
                watchdog,
                null));
    }

    @Test void interruptedWaitShouldThrowDreaminaCliException() throws Exception {
        DreaminaCliExecutor longRun = mockCli.newExecutorWithTimeout(60_000L);
        AtomicReference<Throwable> caught = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                longRun.invoke("__sleep_forever", null);
            } catch (Throwable t) {
                caught.set(t);
            }
        });
        worker.start();
        Thread.sleep(300);
        worker.interrupt();
        worker.join(10_000);
        assertTrue(caught.get() instanceof DreaminaCliException);
    }

    @Test void newSubcommandChainEmptyShouldFailValidation() {
        assertThrows(IllegalArgumentException.class, () -> executor.newSubcommandChain());
    }

    @Test void newSubcommandChainNullTokensShouldFailValidation() {
        assertThrows(IllegalArgumentException.class, () -> executor.newSubcommandChain((String[]) null));
    }

    @Test void newSubcommandChainBlankTokenShouldFailValidation() {
        assertThrows(IllegalArgumentException.class, () -> executor.newSubcommandChain("session", "  "));
    }

    @Test void runShouldWrapIOExceptionFromExecutor() {
        DreaminaCliProperties props = new DreaminaCliProperties();
        props.setExecutable(mockCli.scriptPath().toAbsolutePath().toString());
        props.setCommandTimeoutMillis(5_000L);
        DreaminaCliExecutor failing = new DreaminaCliExecutor(props) {
            @Override
            DefaultExecutor newRunExecutor() {
                return new DefaultExecutor() {
                    @Override
                    public void execute(CommandLine cmd, ExecuteResultHandler handler) throws IOException {
                        throw new IOException("spawn-fail-run");
                    }
                };
            }
        };
        assertThrows(DreaminaCliExecutableFailureException.class, failing::version);
    }

    @Test void failedToStartHelperShouldWrapIOException() {
        CommandLine cmd = new CommandLine("dreamina");
        assertThrows(
            DreaminaCliExecutableFailureException.class,
            () -> {
                throw DreaminaCliExecutor.failedToStart(cmd, new IOException("spawn-fail"));
            });
    }

    @Test void failedAsyncHelperShouldWrapGenericFailure() {
        CommandLine cmd = new CommandLine("dreamina");
        DreaminaCliResult partial = DreaminaCliResult.builder()
            .stdout("").stderr("").exitCode(1).success(false)
            .parsed(DreaminaParsedFields.builder().build())
            .build();
        DreaminaCliException ex = DreaminaCliExecutor.failedAsync(cmd, new RuntimeException("async"), partial);
        assertTrue(ex.getMessage().contains("async failure"));
    }

    @Test void missingExitCodeHelperShouldWrapIllegalState() {
        CommandLine cmd = new CommandLine("dreamina");
        DreaminaCliResult partial = DreaminaCliResult.builder()
            .stdout("").stderr("").exitCode(null).success(false)
            .parsed(DreaminaParsedFields.builder().build())
            .build();
        DreaminaCliException ex = DreaminaCliExecutor.missingExitCode(
            cmd, new IllegalStateException("no exit"), partial);
        assertTrue(ex.getMessage().contains("without observable exit code"));
    }

    @Test void nonZeroExitHelperShouldWrapResult() {
        CommandLine cmd = new CommandLine("dreamina");
        DreaminaCliResult failed = DreaminaCliResult.builder()
            .stdout("").stderr("err").exitCode(1).success(false)
            .parsed(DreaminaParsedFields.builder().build())
            .build();
        DreaminaCliNonZeroExitException ex =
            DreaminaCliExecutor.nonZeroExitWithoutExecuteException(cmd, 1, failed);
        assertEquals(1, ex.getPartialResult().getExitCode());
    }

    @Test void appendQuotedKvInvalidKeyViaReflectionShouldFail() throws Exception {
        Method method = DreaminaCliExecutor.class.getDeclaredMethod(
            "appendQuotedKv", CommandLine.class, String.class, String.class);
        method.setAccessible(true);
        CommandLine cmd = new CommandLine("dreamina");
        assertThrows(IllegalArgumentException.class, () -> invokeReflect(method, null, cmd, "prompt", "x"));
    }

    @Test void appendQuotedKvKeyWithTrailingEqualsViaReflection() throws Exception {
        Method method = DreaminaCliExecutor.class.getDeclaredMethod(
            "appendQuotedKv", CommandLine.class, String.class, String.class);
        method.setAccessible(true);
        CommandLine cmd = new CommandLine("dreamina");
        method.invoke(null, cmd, "--prompt=", "hello");
        assertEquals(1, cmd.getArguments().length);
        assertTrue(cmd.getArguments()[0].contains("--prompt=hello"));
    }

    @Test void snapshotNullStreamsViaReflection() throws Exception {
        Method method = DreaminaCliExecutor.class.getDeclaredMethod(
            "snapshot", String.class, String.class, Integer.class, DreaminaParsedFields.class);
        method.setAccessible(true);
        DreaminaCliResult result = (DreaminaCliResult) method.invoke(
            null, null, null, 1, DreaminaParsedFields.builder().build());
        assertEquals("", result.getStdout());
        assertEquals("", result.getStderr());
    }

    @Test void readExitQuietlyWithoutExitViaReflection() throws Exception {
        Method method = DreaminaCliExecutor.class.getDeclaredMethod(
            "readExitQuietly", DefaultExecuteResultHandler.class);
        method.setAccessible(true);
        assertNull(method.invoke(null, new DefaultExecuteResultHandler()));
    }

    @Test void normalizeInvalidExitValueViaReflection() throws Exception {
        Method method = DreaminaCliExecutor.class.getDeclaredMethod("normalizeExitValue", int.class);
        method.setAccessible(true);
        assertNull(method.invoke(null, Executor.INVALID_EXITVALUE));
    }

    private static void invokeReflect(Method method, Object target, Object... args) throws Exception {
        try {
            method.invoke(target, args);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof Exception e) {
                throw e;
            }
            throw ex;
        }
    }

    /**
     * 可控的 {@link DefaultExecuteResultHandler}，用于覆盖 {@link DreaminaCliExecutor#completeAfterWait} 分支。
     */
    private static final class StubHandler extends DefaultExecuteResultHandler {

        private final Integer exitCode;
        private final boolean missingExit;

        private StubHandler(Integer exitCode, boolean missingExit) {
            this.exitCode = exitCode;
            this.missingExit = missingExit;
        }

        @Override
        public ExecuteException getException() {
            return null;
        }

        @Override
        public int getExitValue() {
            if (missingExit) {
                throw new IllegalStateException("no exit yet");
            }
            return exitCode == null ? 0 : exitCode;
        }
    }
}
