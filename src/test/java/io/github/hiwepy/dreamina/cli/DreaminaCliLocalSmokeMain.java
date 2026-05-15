package io.github.hiwepy.dreamina.cli;

import io.github.hiwepy.dreamina.DreaminaCliProperties;
import io.github.hiwepy.dreamina.exception.DreaminaCliExecutableFailureException;
import io.github.hiwepy.dreamina.exception.DreaminaCliNonZeroExitException;
import io.github.hiwepy.dreamina.exception.DreaminaCliTimeoutException;
import io.github.hiwepy.dreamina.cli.DreaminaCliSubcommands;
import io.github.hiwepy.dreamina.cli.opts.DreaminaFrames2VideoRequest;
import io.github.hiwepy.dreamina.cli.opts.DreaminaImage2ImageRequest;
import io.github.hiwepy.dreamina.cli.opts.DreaminaImage2VideoRequest;
import io.github.hiwepy.dreamina.cli.opts.DreaminaImageModelVersion;
import io.github.hiwepy.dreamina.cli.opts.DreaminaImageResolutionType;
import io.github.hiwepy.dreamina.cli.opts.DreaminaImageUpscaleRequest;
import io.github.hiwepy.dreamina.cli.opts.DreaminaMultiframe2VideoRequest;
import io.github.hiwepy.dreamina.cli.opts.DreaminaMultimodal2VideoRequest;
import io.github.hiwepy.dreamina.cli.opts.DreaminaRatio;
import io.github.hiwepy.dreamina.cli.opts.DreaminaText2ImageRequest;
import io.github.hiwepy.dreamina.cli.opts.DreaminaText2VideoRequest;
import io.github.hiwepy.dreamina.cli.opts.DreaminaVideoModelVersion;
import io.github.hiwepy.dreamina.cli.opts.DreaminaVideoResolutionType;
import io.github.hiwepy.dreamina.cli.DreaminaCliResult;
import io.github.hiwepy.dreamina.cli.DreaminaCliTypedResult;
import io.github.hiwepy.dreamina.cli.DreaminaDeviceLoginResult;
import io.github.hiwepy.dreamina.cli.DreaminaGenerateSubmitResult;
import io.github.hiwepy.dreamina.cli.DreaminaHelpResult;
import io.github.hiwepy.dreamina.cli.DreaminaLoginResult;
import io.github.hiwepy.dreamina.cli.DreaminaQueryResult;
import io.github.hiwepy.dreamina.cli.DreaminaSessionListResult;
import io.github.hiwepy.dreamina.cli.DreaminaSessionMutationResult;
import io.github.hiwepy.dreamina.cli.DreaminaSessionSearchResult;
import io.github.hiwepy.dreamina.cli.DreaminaTaskListResult;
import io.github.hiwepy.dreamina.cli.DreaminaUserCreditResult;
import io.github.hiwepy.dreamina.cli.DreaminaVersionResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import io.github.hiwepy.dreamina.util.DreaminaStrings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 本地结构化验收：通过 {@link DreaminaCliExecutor} 的真实 {@code dreamina} 子进程调用，
 * 覆盖帮助 / 会话 / 任务列表 / 查询以及（可选）全部生成提交路径。
 * <p>
 * 在 {@code dreamina-java-sdk} 模块根目录执行：
 * </p>
 * <pre>
 * mvn -q test-compile exec:java \
 *   -Dexec.mainClass=io.github.hiwepy.dreamina.cli.DreaminaCliLocalSmokeMain \
 *   -Dexec.classpathScope=test
 * </pre>
 * <p>
 * 环境变量：
 * </p>
 * <ul>
 *   <li>{@code DREAMINA_CLI_EXECUTABLE}：覆盖默认可执行文件名。</li>
 *   <li>{@code DREAMINA_SMOKE_SKIP_GENERATE=true}：跳过生成与积分消耗，仅跑只读命令。</li>
 * </ul>
 *
 * @author wandl
 * @since 1.0.0
 */
public final class DreaminaCliLocalSmokeMain {

    /**
     * 1×1 透明 PNG（极小体量，供图生图 / 视频路径校验）。
     */
    private static final byte[] TINY_PNG_BYTES = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    private DreaminaCliLocalSmokeMain() {
    }

    /**
     * 顺序执行结构化封装命令并打印摘要。
     *
     * @param args 未使用
     */
    public static void main(String[] args) throws Exception {
        DreaminaCliProperties props = new DreaminaCliProperties();
        String exe = System.getenv("DREAMINA_CLI_EXECUTABLE");
        if (DreaminaStrings.isNotBlank(exe)) {
            props.setExecutable(exe.trim());
        }
        props.setCommandTimeoutMillis(240_000L);
        DreaminaCliExecutor executor = new DreaminaCliExecutor(props);

        Map<String, String> report = new LinkedHashMap<>();

        // --- 只读：版本 / 额度 / 帮助 / 登录 headless（复用态） / 会话 / 任务 ---
        runStep(report, "versionInfo", () -> {
            DreaminaCliTypedResult<DreaminaVersionResult> t = executor.versionInfo();
            printTyped("versionInfo", t.getRaw(), t.getStructured());
            Objects.requireNonNull(t.getStructured().getVersion(), "version field");
        });
        runStep(report, "userCreditInfo", () -> {
            DreaminaCliTypedResult<DreaminaUserCreditResult> t = executor.userCreditInfo();
            printTyped("userCreditInfo", t.getRaw(), t.getStructured());
            Objects.requireNonNull(t.getStructured().getTotalCredit(), "totalCredit");
        });
        runStep(report, "helpInfo(root)", () -> {
            DreaminaCliTypedResult<DreaminaHelpResult> t = executor.helpInfo();
            printTyped("helpInfo(root)", t.getRaw(), abbreviate(t.getStructured().getFullText(), 400));
        });
        runStep(report, "helpInfo(session)", () -> {
            DreaminaCliTypedResult<DreaminaHelpResult> t = executor.helpInfo(DreaminaCliSubcommands.Account.SESSION);
            printTyped("helpInfo(session)", t.getRaw(), abbreviate(t.getStructured().getFullText(), 400));
        });
        runStep(report, "helpInfo(text2image)", () -> {
            DreaminaCliTypedResult<DreaminaHelpResult> t = executor.helpInfo(DreaminaCliSubcommands.Image.TEXT2IMAGE);
            printTyped("helpInfo(text2image)", t.getRaw(), abbreviate(t.getStructured().getFullText(), 400));
        });
        runStep(report, "helpImageUpscale", () -> {
            DreaminaCliTypedResult<DreaminaHelpResult> t =
                executor.helpInfo(DreaminaCliSubcommands.Image.IMAGE_UPSCALE);
            printTyped("helpImageUpscale", t.getRaw(), abbreviate(t.getStructured().getFullText(), 400));
        });
        runStep(report, "loginHeadlessInfo", () -> {
            DreaminaCliTypedResult<DreaminaLoginResult> t = executor.loginHeadlessInfo();
            printTyped(
                "loginHeadlessInfo",
                t.getRaw(),
                "reused=" + t.getStructured().getOauthSessionReused()
                    + " device=" + (t.getStructured().getDevice() != null));
        });
        runStep(report, "sessionListInfo", () -> {
            DreaminaCliTypedResult<DreaminaSessionListResult> t = executor.sessionListInfo();
            printTyped("sessionListInfo", t.getRaw(), "rows=" + t.getStructured().getRows().size());
        });
        runStep(report, "sessionSearchInfo", () -> {
            DreaminaCliTypedResult<DreaminaSessionSearchResult> t = executor.sessionSearchInfo("default");
            printTyped("sessionSearchInfo", t.getRaw(), "matches=" + t.getStructured().getMatches().size());
        });
        runStep(report, "listTaskInfo", () -> {
            DreaminaCliTypedResult<DreaminaTaskListResult> t = executor.listTaskInfo();
            printTyped(
                "listTaskInfo",
                t.getRaw(),
                "taskCount=" + (t.getStructured().getTaskCount() == null ? "null" : t.getStructured().getTaskCount()));
        });

        // --- 会话变更：创建后立即改名，避免误删用户会话 ---
        final String[] createdIdHolder = new String[1];
        runStep(report, "sessionCreateInfo", () -> {
            String stamp = Long.toString(System.currentTimeMillis());
            DreaminaCliTypedResult<DreaminaSessionMutationResult> t =
                executor.sessionCreateInfo(Collections.singletonList("cli-java-smoke-" + stamp));
            printTyped("sessionCreateInfo", t.getRaw(), t.getStructured());
            createdIdHolder[0] = t.getStructured().getSessionId();
            if (createdIdHolder[0] == null) {
                throw new IllegalStateException("session id parse null");
            }
        });
        runStep(report, "sessionRenameInfo", () -> {
            String sid = createdIdHolder[0];
            if (sid == null) {
                throw new IllegalStateException("skip rename — create failed");
            }
            DreaminaCliTypedResult<DreaminaSessionMutationResult> t =
                executor.sessionRenameInfo(sid, "cli-java-smoke-renamed");
            printTyped("sessionRenameInfo", t.getRaw(), t.getStructured());
        });

        boolean skipGen = truthyEnv("DREAMINA_SMOKE_SKIP_GENERATE");
        if (skipGen) {
            System.out.println("\nDREAMINA_SMOKE_SKIP_GENERATE=true — 跳过生成与 query_result（积分零消耗路径）。");
            printReport(report);
            return;
        }

        Path tinyPng = prepareTinyPng();
        String tinyPath = tinyPng.toAbsolutePath().normalize().toString();

        List<String> submitIds = new ArrayList<>();

        // --- 生成提交：最低参数（仍会消耗积分；失败区分平台并发等） ---
        runStep(report, "text2ImageSubmit", () -> {
            DreaminaCliTypedResult<DreaminaGenerateSubmitResult> t =
                executor.text2ImageSubmit("smoke", Arrays.asList(
                    "--model_version=3.0",
                    "--resolution_type=1k",
                    "--ratio=1:1",
                    "--poll=0"));
            printTyped("text2ImageSubmit", t.getRaw(), summarizeSubmit(t.getStructured()));
            rememberSubmit(submitIds, t.getStructured());
        });
        runStep(report, "image2ImageSubmit", () -> {
            DreaminaCliTypedResult<DreaminaGenerateSubmitResult> t =
                executor.image2ImageSubmit(tinyPath, "smoke", Arrays.asList(
                    "--resolution_type=2k",
                    "--ratio=1:1",
                    "--poll=0"));
            printTyped("image2ImageSubmit", t.getRaw(), summarizeSubmit(t.getStructured()));
            rememberSubmit(submitIds, t.getStructured());
        });
        runStep(report, "imageUpscaleSubmit", () -> {
            DreaminaCliTypedResult<DreaminaGenerateSubmitResult> t = executor.imageUpscaleSubmit(Arrays.asList(
                "--image=" + tinyPath,
                "--resolution_type=2k",
                "--poll=0"));
            printTyped("imageUpscaleSubmit", t.getRaw(), summarizeSubmit(t.getStructured()));
            rememberSubmit(submitIds, t.getStructured());
        });
        runStep(report, "text2VideoSubmit", () -> {
            DreaminaCliTypedResult<DreaminaGenerateSubmitResult> t =
                executor.text2VideoSubmit("smoke", Arrays.asList(
                    "--duration=4",
                    "--video_resolution=720p",
                    "--ratio=1:1",
                    "--poll=0"));
            printTyped("text2VideoSubmit", t.getRaw(), summarizeSubmit(t.getStructured()));
            rememberSubmit(submitIds, t.getStructured());
        });
        runStep(report, "image2VideoSubmit", () -> {
            DreaminaCliTypedResult<DreaminaGenerateSubmitResult> t =
                executor.image2VideoSubmit(tinyPath, "smoke", Arrays.asList(
                    "--duration=4",
                    "--video_resolution=720p",
                    "--poll=0"));
            printTyped("image2VideoSubmit", t.getRaw(), summarizeSubmit(t.getStructured()));
            rememberSubmit(submitIds, t.getStructured());
        });
        runStep(report, "frames2VideoSubmit", () -> {
            DreaminaCliTypedResult<DreaminaGenerateSubmitResult> t = executor.frames2VideoSubmit(Arrays.asList(
                "--first=" + tinyPath,
                "--last=" + tinyPath,
                "--prompt=smoke",
                "--duration=4",
                "--video_resolution=720p",
                "--model_version=seedance2.0fast",
                "--poll=0"));
            printTyped("frames2VideoSubmit", t.getRaw(), summarizeSubmit(t.getStructured()));
            rememberSubmit(submitIds, t.getStructured());
        });
        runStep(report, "multiframe2VideoSubmit", () -> {
            DreaminaCliTypedResult<DreaminaGenerateSubmitResult> t = executor.multiframe2VideoSubmit(Arrays.asList(
                "--images=" + tinyPath + "," + tinyPath,
                "--prompt=smoke",
                "--duration=3",
                "--poll=0"));
            printTyped("multiframe2VideoSubmit", t.getRaw(), summarizeSubmit(t.getStructured()));
            rememberSubmit(submitIds, t.getStructured());
        });
        runStep(report, "multimodal2VideoSubmit", () -> {
            DreaminaCliTypedResult<DreaminaGenerateSubmitResult> t = executor.multimodal2VideoSubmit(Arrays.asList(
                "--image=" + tinyPath,
                "--prompt=smoke",
                "--duration=4",
                "--ratio=1:1",
                "--video_resolution=720p",
                "--model_version=seedance2.0fast",
                "--poll=0"));
            printTyped("multimodal2VideoSubmit", t.getRaw(), summarizeSubmit(t.getStructured()));
            rememberSubmit(submitIds, t.getStructured());
        });

        // --- query_result：抽检首个可用 submitId ---
        runStep(report, "queryResultInfo(spot)", () -> {
            String sid = submitIds.stream().filter(Objects::nonNull).findFirst().orElse(null);
            if (sid == null) {
                throw new IllegalStateException("no submit id captured from generation steps");
            }
            DreaminaCliTypedResult<DreaminaQueryResult> t = executor.queryResultInfo(sid);
            printTyped(
                "queryResultInfo",
                t.getRaw(),
                "submitId=" + t.getStructured().getSubmitId()
                    + " genStatus=" + t.getStructured().getGenStatus()
                    + " failReason=" + abbreviate(String.valueOf(t.getStructured().getFailReason()), 120));
        });

        printReport(report);
    }

    /**
     * 记录 submitId（若结构化字段缺失则跳过）。
     */
    private static void rememberSubmit(List<String> sink, DreaminaGenerateSubmitResult dto) {
        if (dto != null && DreaminaStrings.isNotBlank(dto.getSubmitId())) {
            sink.add(dto.getSubmitId());
        }
    }

    /**
     * 提交摘要：便于肉眼核对解析链路。
     */
    private static String summarizeSubmit(DreaminaGenerateSubmitResult s) {
        return "submitId=" + s.getSubmitId()
            + " genStatus=" + s.getGenStatus()
            + " failReason=" + abbreviate(String.valueOf(s.getFailReason()), 160)
            + " credit=" + s.getCreditCount();
    }

    /**
     * 单步执行包装：捕获 Dreamina 执行层异常并标记失败原因。
     */
    private static void runStep(Map<String, String> report, String label, Runnable runnable) {
        try {
            runnable.run();
            report.put(label, "OK");
        } catch (DreaminaCliNonZeroExitException ex) {
            DreaminaCliResult snap = ex.getPartialResult();
            report.put(label, "CLI_NONZERO exit=" + snap.getExitCode() + " msg=" + abbreviate(ex.getMessage(), 160));
            System.err.println("FAIL " + label + " -> " + report.get(label));
            System.err.println(truncCombinedSnap(snap));
        } catch (DreaminaCliTimeoutException ex) {
            report.put(label, "TIMEOUT " + abbreviate(ex.getMessage(), 160));
            System.err.println("FAIL " + label + " -> " + report.get(label));
        } catch (DreaminaCliExecutableFailureException ex) {
            report.put(label, "SPAWN_FAIL " + abbreviate(ex.getMessage(), 160));
            System.err.println("FAIL " + label + " -> " + report.get(label));
        } catch (Exception ex) {
            report.put(label, "ERROR " + abbreviate(ex.toString(), 200));
            System.err.println("FAIL " + label + " -> " + report.get(label));
            ex.printStackTrace(System.err);
        }
    }

    private static void printTyped(String label, DreaminaCliResult raw, Object structuredPreview) {
        System.out.println("\n--- " + label + " --- success=" + raw.isSuccess() + " exit=" + raw.getExitCode());
        System.out.println("structured: " + formatStructuredPreview(structuredPreview));
        System.out.println("stdout.head=" + abbreviate(raw.getStdout(), 280));
        if (DreaminaStrings.isNotBlank(raw.getStderr())) {
            System.out.println("stderr.head=" + abbreviate(raw.getStderr(), 280));
        }
    }

    /**
     * 将常见结构化 DTO 格式化为单行可读摘要（避免默认 {@link Object#toString()} 噪声）。
     */
    private static String formatStructuredPreview(Object preview) {
        if (preview == null) {
            return "null";
        }
        if (preview instanceof String) {
            String s = (String) preview;
            return s;
        }
        if (preview instanceof DreaminaVersionResult) {
            DreaminaVersionResult v = (DreaminaVersionResult) preview;
            return "version=" + v.getVersion() + " commit=" + v.getCommit() + " buildTime=" + v.getBuildTime();
        }
        if (preview instanceof DreaminaUserCreditResult) {
            DreaminaUserCreditResult c = (DreaminaUserCreditResult) preview;
            return "totalCredit=" + c.getTotalCredit() + " userId=" + c.getUserId() + " vip=" + c.getVipLevel();
        }
        if (preview instanceof DreaminaSessionMutationResult) {
            DreaminaSessionMutationResult m = (DreaminaSessionMutationResult) preview;
            return "kind=" + m.getKind() + " id=" + m.getSessionId() + " name=" + m.getSessionName();
        }
        return preview.toString();
    }

    private static void printReport(Map<String, String> report) {
        System.out.println("\n========== SUMMARY ==========");
        report.forEach((k, v) -> System.out.println(k + " -> " + v));
    }

    /**
     * 在模块 {@code target/dreamina-smoke} 下落地极小 PNG。
     */
    private static Path prepareTinyPng() throws IOException {
        Path dir = Paths.get("target", "dreamina-smoke");
        Files.createDirectories(dir);
        Path png = dir.resolve("tiny.png");
        if (!Files.exists(png) || Files.size(png) == 0) {
            Files.write(png, TINY_PNG_BYTES);
        }
        return png;
    }

    private static boolean truthyEnv(String key) {
        String v = System.getenv(key);
        if (v == null) {
            return false;
        }
        String t = v.trim().toLowerCase();
        return t.equals("1") || t.equals("true") || t.equals("yes");
    }

    private static String abbreviate(String s, int maxChars) {
        if (s == null) {
            return "";
        }
        String t = s.replace('\n', ' ').trim();
        if (t.length() <= maxChars) {
            return t;
        }
        return t.substring(0, maxChars) + "...[" + t.length() + " chars]";
    }

    private static String truncCombinedSnap(DreaminaCliResult snap) {
        if (snap == null) {
            return "(no snapshot)";
        }
        return "stdout=" + abbreviate(snap.getStdout(), 800) + "\nstderr=" + abbreviate(snap.getStderr(), 800);
    }
}
