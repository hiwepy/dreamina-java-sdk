package io.github.hiwepy.dreamina.cli.availability;

import io.github.hiwepy.dreamina.DreaminaCliProperties;
import io.github.hiwepy.dreamina.cli.DreaminaCliExecutor;
import io.github.hiwepy.dreamina.cli.DreaminaCliResult;
import io.github.hiwepy.dreamina.exception.DreaminaCliException;
import io.github.hiwepy.dreamina.exception.DreaminaCliExecutableFailureException;
import io.github.hiwepy.dreamina.exception.DreaminaCliNonZeroExitException;
import io.github.hiwepy.dreamina.exception.DreaminaCliTimeoutException;
import io.github.hiwepy.dreamina.util.DreaminaStrings;
import java.io.File;
import java.util.Objects;
import java.util.Optional;

/**
 * 探测本机 {@code dreamina} CLI 是否已安装且可执行 {@code dreamina version}。
 * <p>
 * 供 Spring Boot Starter 在启动阶段调用，也可在纯 Java 应用中手动执行就绪检查。
 * </p>
 *
 * @author wandl
 * @since 1.0.0
 */
public class DreaminaCliAvailabilityChecker {

    /**
     * 使用给定执行器与其绑定配置执行探测（推荐：与运行时共用同一 {@link DreaminaCliExecutor} Bean）。
     *
     * @param executor 已构造的执行器，不得为 null
     * @return 探测报告
     */
    public DreaminaCliAvailabilityReport check(DreaminaCliExecutor executor) {
        Objects.requireNonNull(executor, "executor");
        return check(executor.getProperties());
    }

    /**
     * 根据配置构造临时执行器并探测 CLI 可用性。
     *
     * @param properties CLI 配置，不得为 null
     * @return 探测报告
     */
    public DreaminaCliAvailabilityReport check(DreaminaCliProperties properties) {
        Objects.requireNonNull(properties, "properties");
        String configured = properties.getExecutable();
        if (DreaminaStrings.isBlank(configured)) {
            return unavailable(
                    DreaminaCliAvailabilityStatus.EXECUTABLE_NOT_CONFIGURED,
                    configured,
                    null,
                    "dreamina.cli.executable is blank",
                    null);
        }
        String trimmed = configured.trim();
        Optional<String> resolved = resolveExecutablePath(trimmed);
        if (!resolved.isPresent()) {
            if (looksLikePath(trimmed)) {
                File file = new File(trimmed);
                if (!file.exists()) {
                    return unavailable(
                            DreaminaCliAvailabilityStatus.EXECUTABLE_NOT_FOUND,
                            trimmed,
                            null,
                            "executable file does not exist: " + file.getAbsolutePath(),
                            null);
                }
                return unavailable(
                        DreaminaCliAvailabilityStatus.EXECUTABLE_NOT_EXECUTABLE,
                        trimmed,
                        file.getAbsolutePath(),
                        "executable exists but is not executable: " + file.getAbsolutePath(),
                        null);
            }
            return unavailable(
                    DreaminaCliAvailabilityStatus.EXECUTABLE_NOT_FOUND,
                    trimmed,
                    null,
                    "executable not found on PATH: " + trimmed,
                    null);
        }

        DreaminaCliProperties probeProps = copyForProbe(properties);
        DreaminaCliExecutor probeExecutor = new DreaminaCliExecutor(probeProps);
        try {
            DreaminaCliResult result = probeExecutor.version();
            return DreaminaCliAvailabilityReport.builder()
                    .status(DreaminaCliAvailabilityStatus.AVAILABLE)
                    .available(true)
                    .configuredExecutable(trimmed)
                    .resolvedExecutablePath(resolved.get())
                    .message("dreamina version succeeded")
                    .probeResult(result)
                    .build();
        } catch (DreaminaCliTimeoutException ex) {
            return unavailable(
                    DreaminaCliAvailabilityStatus.TIMEOUT,
                    trimmed,
                    resolved.get(),
                    ex.getMessage(),
                    ex.getPartialResult());
        } catch (DreaminaCliNonZeroExitException ex) {
            return unavailable(
                    DreaminaCliAvailabilityStatus.NON_ZERO_EXIT,
                    trimmed,
                    resolved.get(),
                    ex.getMessage(),
                    ex.getPartialResult());
        } catch (DreaminaCliExecutableFailureException ex) {
            return unavailable(
                    DreaminaCliAvailabilityStatus.SPAWN_FAILED,
                    trimmed,
                    resolved.get(),
                    ex.getMessage(),
                    null);
        } catch (DreaminaCliException ex) {
            return unavailable(
                    DreaminaCliAvailabilityStatus.FAILED,
                    trimmed,
                    resolved.get(),
                    ex.getMessage(),
                    ex.getPartialResult());
        }
    }

    /**
     * 复制配置并缩短单次探测超时，避免启动检查占用默认命令超时。
     */
    private static DreaminaCliProperties copyForProbe(DreaminaCliProperties source) {
        DreaminaCliProperties copy = new DreaminaCliProperties();
        copy.setExecutable(source.getExecutable());
        copy.setWorkingDirectory(source.getWorkingDirectory());
        copy.setMaxConcurrentExecutions(source.getMaxConcurrentExecutions());
        copy.setDefaultPollIntervalSeconds(source.getDefaultPollIntervalSeconds());
        long probeTimeout = source.getStartupProbeTimeoutMillis();
        if (probeTimeout <= 0) {
            probeTimeout = 30_000L;
        }
        copy.setCommandTimeoutMillis(probeTimeout);
        copy.setStartupProbeTimeoutMillis(probeTimeout);
        return copy;
    }

    /**
     * 解析可执行文件：绝对/相对路径直接检查；否则在 {@code PATH} 中查找。
     */
    static Optional<String> resolveExecutablePath(String executable) {
        if (DreaminaStrings.isBlank(executable)) {
            return Optional.empty();
        }
        String trimmed = executable.trim();
        File direct = new File(trimmed);
        if (looksLikePath(trimmed)) {
            if (direct.isFile() && direct.canExecute()) {
                return Optional.of(direct.getAbsolutePath());
            }
            return Optional.empty();
        }
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null || pathEnv.isEmpty()) {
            return Optional.empty();
        }
        for (String dir : pathEnv.split(File.pathSeparator)) {
            if (DreaminaStrings.isBlank(dir)) {
                continue;
            }
            File candidate = new File(dir.trim(), trimmed);
            if (candidate.isFile() && candidate.canExecute()) {
                return Optional.of(candidate.getAbsolutePath());
            }
        }
        return Optional.empty();
    }

    private static boolean looksLikePath(String executable) {
        return executable.contains("/") || executable.contains("\\") || new File(executable).isAbsolute();
    }

    private static DreaminaCliAvailabilityReport unavailable(
            DreaminaCliAvailabilityStatus status,
            String configured,
            String resolved,
            String message,
            DreaminaCliResult partial) {
        return DreaminaCliAvailabilityReport.builder()
                .status(status)
                .available(false)
                .configuredExecutable(configured)
                .resolvedExecutablePath(resolved)
                .message(message)
                .probeResult(partial)
                .build();
    }
}
