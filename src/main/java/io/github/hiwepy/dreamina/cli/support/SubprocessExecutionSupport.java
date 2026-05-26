package io.github.hiwepy.dreamina.cli.support;

import lombok.Getter;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecuteResultHandler;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 基于 Apache Commons Exec 的子进程执行辅助：Watchdog 超时、有界 {@code waitFor}、并发限流。
 * <p>
 * 参考 Playwright Starter 中 {@code DefaultExecutor.builder()} + {@link ExecuteWatchdog} 模式，
 * 并补充异步 handler 与 Semaphore，避免高并发下线程因僵死子进程永久阻塞。
 * </p>
 *
 * @author wandl
 * @since 1.0.0
 */
public final class SubprocessExecutionSupport {

    /** Watchdog 触发后，handler 收尾等待的上限（毫秒）。 */
    public static final long WAIT_GRACE_MILLIS = 5_000L;

    private static final int DEFAULT_MAX_CONCURRENT = Math.max(2, Runtime.getRuntime().availableProcessors());

    private static final AtomicReference<Semaphore> CONCURRENCY_LIMIT =
            new AtomicReference<>(new Semaphore(DEFAULT_MAX_CONCURRENT));

    private SubprocessExecutionSupport() {
    }

    /**
     * 配置本机 CLI 子进程全局并发上限；{@code maxConcurrent <= 0} 时恢复为默认值。
     *
     * @param maxConcurrent 允许同时运行的子进程数
     */
    public static void configureMaxConcurrentExecutions(int maxConcurrent) {
        if (maxConcurrent <= 0) {
            CONCURRENCY_LIMIT.set(new Semaphore(DEFAULT_MAX_CONCURRENT));
            return;
        }
        CONCURRENCY_LIMIT.set(new Semaphore(maxConcurrent));
    }

    /**
     * @return 未显式配置时的默认并发上限
     */
    public static int defaultMaxConcurrentExecutions() {
        return DEFAULT_MAX_CONCURRENT;
    }

    /**
     * 在并发许可内启动子进程并阻塞至结束、超时或被强制销毁。
     *
     * @param request 命令行、工作目录、环境与超时
     * @return 捕获的输出与 handler 状态
     */
    public static RunSession execute(ExecutionRequest request) throws IOException, InterruptedException {
        Objects.requireNonNull(request, "request");
        Semaphore limit = CONCURRENCY_LIMIT.get();
        limit.acquire();
        try {
            return executeWithinLimit(request);
        } finally {
            limit.release();
        }
    }

    private static RunSession executeWithinLimit(ExecutionRequest request) throws IOException, InterruptedException {
        long timeoutMs = Math.max(1L, request.getTimeoutMillis());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        DefaultExecutor.Builder builder = DefaultExecutor.builder();
        if (request.getWorkingDirectory() != null) {
            builder.setWorkingDirectory(request.getWorkingDirectory());
        }
        DefaultExecutor executor = builder.get();
        executor.setStreamHandler(new PumpStreamHandler(out, err));

        ExecuteWatchdog watchdog =
                ExecuteWatchdog.builder().setTimeout(Duration.ofMillis(timeoutMs)).get();
        executor.setWatchdog(watchdog);

        DefaultExecuteResultHandler handler = new DefaultExecuteResultHandler();
        Map<String, String> environment = request.getEnvironment();
        if (environment != null) {
            executor.execute(request.getCommandLine(), environment, handler);
        } else {
            executor.execute(request.getCommandLine(), handler);
        }

        boolean finished = awaitResult(handler, timeoutMs + WAIT_GRACE_MILLIS);
        boolean waitTimedOut = !finished;
        if (waitTimedOut) {
            watchdog.destroyProcess();
            awaitResult(handler, WAIT_GRACE_MILLIS);
        }

        return new RunSession(out, err, handler, watchdog, timeoutMs, waitTimedOut);
    }

    private static boolean awaitResult(DefaultExecuteResultHandler handler, long timeoutMillis)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + Math.max(1L, timeoutMillis);
        while (!handler.hasResult()) {
            if (System.currentTimeMillis() >= deadline) {
                return false;
            }
            Thread.sleep(Math.min(50L, deadline - System.currentTimeMillis()));
        }
        return true;
    }

    /**
     * 单次子进程执行请求。
     */
    @Getter
    public static final class ExecutionRequest {

        private final CommandLine commandLine;
        private final File workingDirectory;
        private final Map<String, String> environment;
        private final long timeoutMillis;

        /**
         * @param commandLine      待执行命令行
         * @param workingDirectory 可选工作目录
         * @param environment      可选环境变量覆盖；null 表示继承 JVM 环境
         * @param timeoutMillis    Watchdog 超时（毫秒）
         */
        public ExecutionRequest(
                CommandLine commandLine,
                File workingDirectory,
                Map<String, String> environment,
                long timeoutMillis) {
            this.commandLine = Objects.requireNonNull(commandLine, "commandLine");
            this.workingDirectory = workingDirectory;
            this.environment = environment;
            this.timeoutMillis = timeoutMillis;
        }
    }

    /**
     * 子进程执行会话结果（输出流仍保留，便于上层按需转字符串）。
     */
    @Getter
    public static final class RunSession {

        private final ByteArrayOutputStream stdout;
        private final ByteArrayOutputStream stderr;
        private final DefaultExecuteResultHandler handler;
        private final ExecuteWatchdog watchdog;
        private final long timeoutMillis;
        private final boolean waitTimedOut;

        RunSession(
                ByteArrayOutputStream stdout,
                ByteArrayOutputStream stderr,
                DefaultExecuteResultHandler handler,
                ExecuteWatchdog watchdog,
                long timeoutMillis,
                boolean waitTimedOut) {
            this.stdout = stdout;
            this.stderr = stderr;
            this.handler = handler;
            this.watchdog = watchdog;
            this.timeoutMillis = timeoutMillis;
            this.waitTimedOut = waitTimedOut;
        }

        /**
         * @return Watchdog 杀进程或 handler 等待超时
         */
        public boolean timedOut() {
            return waitTimedOut || watchdog.killedProcess();
        }
    }
}
