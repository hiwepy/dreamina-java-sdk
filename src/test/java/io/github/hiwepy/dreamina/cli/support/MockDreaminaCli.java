package io.github.hiwepy.dreamina.cli.support;

import io.github.hiwepy.dreamina.DreaminaCliProperties;
import io.github.hiwepy.dreamina.cli.DreaminaCliExecutor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 安装可执行的 mock {@code dreamina} 脚本，记录 argv 并返回预设 JSON/文本。
 *
 * @author wandl
 * @since 1.0.0
 */
public final class MockDreaminaCli {

    private static final byte[] TINY_PNG = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    private final Path scriptPath;
    private final Path logPath;
    private final Path mediaDir;

    private MockDreaminaCli(Path scriptPath, Path logPath, Path mediaDir) {
        this.scriptPath = scriptPath;
        this.logPath = logPath;
        this.mediaDir = mediaDir;
    }

    /**
     * 在临时目录安装 mock CLI。
     */
    public static MockDreaminaCli install() throws IOException {
        Path root = Files.createTempDirectory("dreamina-mock-cli-");
        Path script = root.resolve("dreamina");
        Path log = root.resolve("invocations.log");
        Path media = root.resolve("media");
        Files.createDirectories(media);
        Files.writeString(script, buildScript(log), StandardCharsets.UTF_8);
        makeExecutable(script);
        return new MockDreaminaCli(script, log, media);
    }

    /**
     * 构造绑定 mock 可执行文件的执行器。
     */
    public DreaminaCliExecutor newExecutor() {
        DreaminaCliProperties props = new DreaminaCliProperties();
        props.setExecutable(scriptPath.toAbsolutePath().toString());
        props.setCommandTimeoutMillis(5_000L);
        return new DreaminaCliExecutor(props);
    }

    /**
     * 构造短超时执行器，用于触发 watchdog 超时分支。
     */
    public DreaminaCliExecutor newExecutorWithTimeout(long timeoutMs) {
        DreaminaCliProperties props = new DreaminaCliProperties();
        props.setExecutable(scriptPath.toAbsolutePath().toString());
        props.setCommandTimeoutMillis(timeoutMs);
        return new DreaminaCliExecutor(props);
    }

    /**
     * 清空调用日志。
     */
    public void resetLog() throws IOException {
        Files.deleteIfExists(logPath);
    }

    /**
     * 读取全部 mock 调用记录。
     */
    public List<String> invocations() throws IOException {
        if (!Files.exists(logPath)) {
            return List.of();
        }
        return Files.readAllLines(logPath, StandardCharsets.UTF_8);
    }

    /**
     * 最近一条调用 argv 文本。
     */
    public String lastInvocation() throws IOException {
        List<String> lines = invocations();
        return lines.isEmpty() ? "" : lines.get(lines.size() - 1);
    }

    /**
     * 创建 1×1 PNG 临时文件供图生图/视频请求使用。
     */
    public Path newTinyPng(String name) throws IOException {
        Path file = mediaDir.resolve(name);
        Files.write(file, TINY_PNG);
        return file;
    }

    public Path scriptPath() {
        return scriptPath;
    }

    private static String buildScript(Path logPath) {
        String log = logPath.toAbsolutePath().toString().replace("'", "'\\''");
        return """
            #!/usr/bin/env bash
            set -euo pipefail
            LOG='%s'
            printf '%%s\\n' "$*" >> "$LOG"
            cmd="${1:-}"
            shift || true

            case "$cmd" in
              help)
                echo "Usage: dreamina help [flags]"
                ;;
              version)
                echo '{"version":"c58a6a2-dirty","commit":"c58a6a2","build_time":"2026-05-07T09:52:59Z"}'
                ;;
              user_credit)
                echo '{"total_credit":4388,"user_id":1552973852847448,"user_name":"","vip_level":"maestro"}'
                ;;
              login)
                sub="${1:-}"
                if [ "$sub" = "checklogin" ]; then
                  :
                elif printf '%%s' "$*" | grep -q -- '--headless'; then
                  if printf '%%s' "$*" | grep -q -- '--mock-device-flow'; then
                    echo '{"verification_uri":"https://mock/login","user_code":"MOCK","device_code":"dev-mock"}'
                  else
                    echo '已复用当前本地 OAuth 登录态。'
                  fi
                else
                  cat <<'EOF'
已复用当前本地 OAuth 登录态。
当前登录账户信息：
user_id: 1552973852847448
vip_level: maestro
total_credit: 4391
EOF
                fi
                ;;
              logout)
                echo '已清除本地登录态。'
                ;;
              relogin)
                cat <<'EOF'
请使用浏览器完成 OAuth Device Flow 登录。
verification_uri: https://jimeng.jianying.com/ai-tool/cli-auth
user_code: 88d38543ef407cb0a01a61088ec0d32c
device_code: 662eef8f79b0ee3c20d7222c5ec28ed3
poll_interval: 1s
expires_at: 2026-05-26T05:38:58Z
EOF
                ;;
              session)
                sub="${1:-}"
                shift || true
                case "$sub" in
                  create)
                    echo 'Created session "mock-session" (ID: 10001)'
                    ;;
                  list|ls)
                    cat <<'EOF'
ID              NAME                        PINNED  UPDATED_AT
--------------  --------------------------  ------  ----------------
10001           mock-session                No      2026-05-14 10:44
EOF
                    ;;
                  search|find)
                    cat <<'EOF'
Found 1 sessions containing "mock":
ID  NAME          UPDATED_AT
--  ------------  ----------------
10001 mock-session 2026-05-14 10:44
EOF
                    ;;
                  rename|update)
                    echo 'Renamed session 10001 to "mock-renamed"'
                    ;;
                  delete|rm)
                    echo 'deleted'
                    ;;
                  "")
                    cat <<'EOF'
Usage:
  dreamina session [flags]

Manage Dreamina sessions (create, list, search, rename, delete).
EOF
                    ;;
                  *)
                    echo "session sub=$sub"
                    ;;
                esac
                ;;
              list_task)
                echo '[{"submit_id":"mock-submit-1","gen_task_type":"text2image","gen_status":"success","fail_reason":"","result_json":{"images":[{"width":1024,"height":1024}],"videos":[]},"commerce_info":{"credit_count":0,"triplet":{"resource_type":"","resource_id":"","benefit_type":""},"triplets":[{"resource_type":"aigc","resource_id":"generate_img","benefit_type":"image_uhd_4k"}]}}]'
                ;;
              query_result)
                echo '{"submit_id":"mock-submit-1","gen_status":"success","credit_count":3,"result_json":{"images":[{"image_url":"https://mock/img.png","width":2048,"height":2048}],"videos":[]},"queue_info":{"queue_idx":0,"priority":1,"queue_status":"Finish","queue_length":0}}'
                ;;
              text2image|text2video|image2image|image_upscale|image2video|frames2video|multiframe2video|multimodal2video)
                echo '{"submit_id":"mock-gen-1","logid":"202605260533251720170000026033C60","gen_status":"querying","credit_count":3}'
                ;;
              __exit_nonzero)
                echo 'fail' >&2
                exit 7
                ;;
              __exit_one)
                exit 1
                ;;
              __sleep_forever)
                sleep 60
                ;;
              *)
                echo "unknown cmd=$cmd" >&2
                exit 2
                ;;
            esac
            """.formatted(log);
    }

    private static void makeExecutable(Path script) throws IOException {
        try {
            Set<PosixFilePermission> perms = EnumSet.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE);
            Files.setPosixFilePermissions(script, perms);
        } catch (UnsupportedOperationException ex) {
            script.toFile().setExecutable(true);
        }
    }
}
