package io.github.hiwepy.dreamina.cli.parser;

import io.github.hiwepy.dreamina.cli.DreaminaCliResponse;
import io.github.hiwepy.dreamina.cli.DreaminaCliResult;
import io.github.hiwepy.dreamina.cli.model.DreaminaCheckLogin;
import io.github.hiwepy.dreamina.cli.model.DreaminaDeviceLogin;
import io.github.hiwepy.dreamina.cli.model.DreaminaGenerateSubmit;
import io.github.hiwepy.dreamina.cli.model.DreaminaHelp;
import io.github.hiwepy.dreamina.cli.model.DreaminaLogin;
import io.github.hiwepy.dreamina.cli.model.DreaminaLogout;
import io.github.hiwepy.dreamina.cli.model.DreaminaQueryQueueDebugInfo;
import io.github.hiwepy.dreamina.cli.model.DreaminaQueryResult;
import io.github.hiwepy.dreamina.cli.model.DreaminaRelogin;
import io.github.hiwepy.dreamina.cli.model.DreaminaSessionList;
import io.github.hiwepy.dreamina.cli.model.DreaminaSessionMutation;
import io.github.hiwepy.dreamina.cli.model.DreaminaSessionSearch;
import io.github.hiwepy.dreamina.cli.model.DreaminaTaskItem;
import io.github.hiwepy.dreamina.cli.model.DreaminaUserCredit;
import io.github.hiwepy.dreamina.cli.model.DreaminaVersion;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DreaminaCliStructuredPayloadMapper} 单元测试：复用真实 CLI JSON / 表格快照片段。
 *
 * @author wandl
 * @since 1.0.0
 */
class DreaminaCliStructuredPayloadMapperTest {

    private static final DreaminaCliStructuredPayloadMapper MAPPER = new DreaminaCliStructuredPayloadMapper();

    /**
     * 从 {@code src/test/resources/cli-audit/} 加载交互式审计 JSON 片段（2026-05 采集）。
     *
     * @param resourceName 资源文件名
     * @return UTF-8 文本
     */
    private static String loadAuditFixture(String resourceName) throws IOException {
        String path = "cli-audit/" + resourceName;
        try (InputStream in = DreaminaCliStructuredPayloadMapperTest.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("missing classpath resource: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * 构造 exit 0 的 {@link DreaminaCliResult}，stdout 为给定 JSON。
     */
    private static DreaminaCliResult jsonStdout(String stdout) {
        return DreaminaCliResult.builder()
            .stdout(stdout)
            .stderr("")
            .exitCode(0)
            .success(true)
            .parsed(DreaminaParsedFields.builder().build())
            .build();
    }

    /**
     * version JSON → structured。
     */
    @Test
    void mapVersion_shouldReadKnownFields() {
        DreaminaCliResult raw = DreaminaCliResult.builder()
            .stdout(
                """
                {
                  "version": "c58a6a2-dirty",
                  "commit": "c58a6a2",
                  "build_time": "2026-05-07T09:52:59Z"
                }
                """)
            .stderr("")
            .exitCode(0)
            .success(true)
            .parsed(DreaminaParsedFields.builder().build())
            .build();
        DreaminaCliStructuredPayloadMapper mapper = new DreaminaCliStructuredPayloadMapper();
        DreaminaCliResponse<DreaminaVersion> response = mapper.mapVersion(raw);
        DreaminaVersion v = response.getBody();
        assertNotNull(v);
        assertEquals("c58a6a2-dirty", v.getVersion());
        assertEquals("c58a6a2", v.getCommit());
        assertEquals("2026-05-07T09:52:59Z", v.getBuildTime());
        assertNotNull(response.getJson());
    }

    /**
     * 生产 {@code user_credit} JSON（2026-05 线上样例：{@code user_name} 可为空串）。
     */
    @Test
    void mapUserCredit_shouldParseProductionResponse() {
        DreaminaCliResult raw = DreaminaCliResult.builder()
            .stdout(
                """
                {
                  "total_credit": 4388,
                  "user_id": 1552973852847448,
                  "user_name": "",
                  "vip_level": "maestro"
                }
                """)
            .stderr("")
            .exitCode(0)
            .success(true)
            .parsed(DreaminaParsedFields.builder().build())
            .build();
        DreaminaCliResponse<DreaminaUserCredit> response =
            new DreaminaCliStructuredPayloadMapper().mapUserCredit(raw);
        DreaminaUserCredit body = response.getBody();
        assertNotNull(body);
        assertEquals(4388L, body.getTotalCredit().longValue());
        assertEquals(1552973852847448L, body.getUserId().longValue());
        assertEquals("", body.getUserName());
        assertEquals("maestro", body.getVipLevel());
        assertNotNull(response.getJson());
    }

    /**
     * 生产 {@code text2image} 提交 JSON：submit_id / logid / gen_status / credit_count。
     */
    @Test
    void mapGenerateSubmit_shouldParseProductionText2ImageResponse() {
        DreaminaCliResult raw = DreaminaCliResult.builder()
            .stdout(
                """
                {
                  "submit_id": "851e46ee-d199-42aa-917e-b2a57095a54d",
                  "logid": "202605260533251720170000026033C60",
                  "gen_status": "querying",
                  "credit_count": 3
                }
                """)
            .stderr("")
            .exitCode(0)
            .success(true)
            .parsed(DreaminaParsedFields.builder().build())
            .build();
        DreaminaCliResponse<DreaminaGenerateSubmit> response =
            new DreaminaCliStructuredPayloadMapper().mapGenerateSubmit(raw);
        DreaminaGenerateSubmit dto = response.getBody();
        assertEquals("851e46ee-d199-42aa-917e-b2a57095a54d", dto.getSubmitId());
        assertEquals("202605260533251720170000026033C60", dto.getLogid());
        assertEquals("querying", dto.getGenStatus());
        assertEquals(3L, dto.getCreditCount().longValue());
        assertNotNull(response.getJson());
    }

    /**
     * list_task JSON 数组 → task list body。
     */
    @Test
    void mapTaskList_shouldCountArrayElements() {
        DreaminaCliResult raw = DreaminaCliResult.builder()
            .stdout(
                """
                [
                  {"submit_id":"a","gen_status":"querying"},
                  {"submit_id":"b","gen_status":"success"}
                ]
                """)
            .stderr("")
            .exitCode(0)
            .success(true)
            .parsed(DreaminaParsedFields.builder().build())
            .build();
        DreaminaCliResponse<List<DreaminaTaskItem>> response =
            new DreaminaCliStructuredPayloadMapper().mapTaskList(raw);
        List<DreaminaTaskItem> tasks = response.getBody();
        assertEquals(2, tasks.size());
        assertEquals("a", tasks.get(0).getSubmitId());
        assertEquals("querying", tasks.get(0).getGenStatus());
        assertNotNull(response.getStdout());
    }

    /**
     * 生产 {@code list_task} 样例：{@code commerce_info}、仅含宽高的 {@code result_json.images}。
     */
    @Test
    void mapTaskList_shouldParseProductionCommerceAndResultJson() {
        DreaminaCliResult raw = DreaminaCliResult.builder()
            .stdout(
                """
                [
                  {
                    "submit_id": "929efc4b-b0a5-4d35-9077-7e3596e74c95",
                    "gen_task_type": "text2image",
                    "gen_status": "success",
                    "fail_reason": "",
                    "result_json": {
                      "images": [{"width": 3520, "height": 4693}],
                      "videos": []
                    },
                    "commerce_info": {
                      "credit_count": 0,
                      "triplet": {
                        "resource_type": "",
                        "resource_id": "",
                        "benefit_type": ""
                      },
                      "triplets": [
                        {
                          "resource_type": "aigc",
                          "resource_id": "generate_img",
                          "benefit_type": "image_uhd_4k"
                        }
                      ]
                    }
                  }
                ]
                """)
            .stderr("")
            .exitCode(0)
            .success(true)
            .parsed(DreaminaParsedFields.builder().build())
            .build();
        DreaminaCliResponse<List<DreaminaTaskItem>> response =
            new DreaminaCliStructuredPayloadMapper().mapTaskList(raw);
        assertEquals(1, response.getBody().size());
        var item = response.getBody().get(0);
        assertEquals("929efc4b-b0a5-4d35-9077-7e3596e74c95", item.getSubmitId());
        assertEquals("text2image", item.getGenTaskType());
        assertEquals("success", item.getGenStatus());
        assertEquals(3520, item.getResultJson().safeImages().get(0).getWidth());
        assertEquals(4693, item.getResultJson().safeImages().get(0).getHeight());
        assertNull(item.getResultJson().safeImages().get(0).getImageUrl());
        assertEquals(0L, item.resolveCreditCount().longValue());
        assertEquals("image_uhd_4k", item.getCommerceInfo().safeTriplets().get(0).getBenefitType());
        assertEquals("generate_img", item.getCommerceInfo().safeTriplets().get(0).getResourceId());
    }

    /**
     * query_result JSON → 强类型 queue_info / result_json。
     */
    @Test
    void mapQueryResult_shouldMapTypedNestedPayload() {
        DreaminaCliResult raw = DreaminaCliResult.builder()
            .stdout(
                """
                {
                  "submit_id": "2fcc4089-f0d3-479c-a42f-f73c838cc626",
                  "gen_status": "success",
                  "fail_reason": "",
                  "result_json": {"images": []},
                  "queue_info": {"queue_status": "Finish"}
                }
                """)
            .stderr("")
            .exitCode(0)
            .success(true)
            .parsed(DreaminaParsedFields.builder().submitId("ignored").build())
            .build();
        DreaminaCliResponse<DreaminaQueryResult> response =
            new DreaminaCliStructuredPayloadMapper().mapQueryResult(raw);
        DreaminaQueryResult q = response.getBody();
        assertNotNull(q);
        assertEquals("2fcc4089-f0d3-479c-a42f-f73c838cc626", q.getSubmitId());
        assertEquals("success", q.getGenStatus());
        assertTrue(q.isGenSuccess());
        assertNotNull(q.getQueueInfo());
        assertEquals("Finish", q.getQueueInfo().getQueueStatus());
        assertTrue(q.isQueueFinished());
        assertNotNull(q.getResultJson());
        assertTrue(q.images().isEmpty());
    }

    /**
     * 与生产环境 {@code dreamina query_result} 样例对齐（配对 {@code text2image} submit_id）。
     */
    @Test
    void mapQueryResult_shouldMapProductionLikePayload() {
        DreaminaCliResult raw = DreaminaCliResult.builder()
            .stdout(
                """
                {
                  "submit_id": "851e46ee-d199-42aa-917e-b2a57095a54d",
                  "gen_status": "success",
                  "result_json": {
                    "images": [
                      {
                        "image_url": "https://p11-dreamina-sign.byteimg.com/tos-cn-i-tb4s082cfz/ea523ee1fdcd4f309ff3e19f511edc9d~tplv-tb4s082cfz-aigc_resize:0:0.png?lk3s=7c3bb0db&x-expires=1779778800&x-signature=OBRi3zPLMEI9PRU%2Fz2T2lrFF5dI%3D&format=.png",
                        "width": 2048,
                        "height": 2048
                      }
                    ],
                    "videos": []
                  },
                  "credit_count": 3,
                  "queue_info": {
                    "queue_idx": 0,
                    "priority": 1,
                    "queue_status": "Finish",
                    "queue_length": 0,
                    "debug_info": "{\\"have_no_dreamina_queue_name\\":true,\\"dreamina_matrix_queue_name\\":\\"\\",\\"dreamina_matrix_req_key\\":\\"\\",\\"dreamina_matrix_second_req_key\\":\\"\\",\\"have_no_queue_name\\":false,\\"queue_name\\":\\"high_aes_general_v50\\",\\"matrix_req_key\\":\\"MImageGen:high_aes_general_v50\\",\\"matrix_second_req_key\\":\\"\\"}"
                  }
                }
                """)
            .stderr("")
            .exitCode(0)
            .success(true)
            .parsed(DreaminaParsedFields.builder().build())
            .build();
        DreaminaQueryResult q = new DreaminaCliStructuredPayloadMapper().mapQueryResult(raw).getBody();
        assertEquals("851e46ee-d199-42aa-917e-b2a57095a54d", q.getSubmitId());
        assertTrue(q.isGenSuccess());
        assertEquals(3L, q.getCreditCount().longValue());
        assertEquals(1, q.images().size());
        assertEquals(2048, q.images().get(0).getWidth().intValue());
        assertEquals(2048, q.images().get(0).getHeight().intValue());
        assertNotNull(q.firstImageUrl());
        assertTrue(q.firstImageUrl().contains("p11-dreamina-sign.byteimg.com"));
        assertTrue(q.firstImageUrl().contains("%2F"));
        assertTrue(q.getResultJson().safeVideos().isEmpty());
        assertNotNull(q.getQueueInfo());
        assertEquals(0, q.getQueueInfo().getQueueIdx().intValue());
        assertEquals(1, q.getQueueInfo().getPriority().intValue());
        assertEquals(0, q.getQueueInfo().getQueueLength().intValue());
        assertEquals("Finish", q.getQueueInfo().getQueueStatus());
        assertTrue(q.isQueueFinished());
        DreaminaQueryQueueDebugInfo debug = q.getQueueInfo().getParsedDebugInfo();
        assertNotNull(debug);
        assertEquals("high_aes_general_v50", debug.getQueueName());
        assertEquals("MImageGen:high_aes_general_v50", debug.getMatrixReqKey());
        assertEquals(Boolean.TRUE, debug.getHaveNoDreaminaQueueName());
        assertEquals(Boolean.FALSE, debug.getHaveNoQueueName());
    }

    /**
     * session list 表格 → 行解析。
     */
    @Test
    void mapSessionList_shouldParseRows() {
        DreaminaCliResult raw = DreaminaCliResult.builder()
            .stdout(
                """
                ID              NAME                        PINNED  UPDATED_AT
                --------------  --------------------------  ------  ----------------
                0               default                     Yes     2026-05-14 19:39
                12978322779916  产康图片修改及比例生成      No      2026-05-14 10:44
                """)
            .stderr("")
            .exitCode(0)
            .success(true)
            .parsed(DreaminaParsedFields.builder().build())
            .build();
        DreaminaSessionList list = new DreaminaCliStructuredPayloadMapper().mapSessionList(raw).getBody();
        assertEquals(2, list.getRows().size());
        assertEquals("0", list.getRows().get(0).getId());
        assertEquals("default", list.getRows().get(0).getName());
        assertEquals("Yes", list.getRows().get(0).getPinned());
    }

    /**
     * session search 精简表格。
     */
    @Test
    void mapSessionSearch_shouldParseMatches() {
        DreaminaCliResult raw = DreaminaCliResult.builder()
            .stdout(
                """
                Found 1 sessions containing "default":
                ID  NAME     UPDATED_AT
                --  -------  ----------------
                0   default  2026-05-14 19:39
                """)
            .stderr("")
            .exitCode(0)
            .success(true)
            .parsed(DreaminaParsedFields.builder().build())
            .build();
        DreaminaSessionSearch s =
            new DreaminaCliStructuredPayloadMapper().mapSessionSearch("default", raw).getBody();
        assertEquals(1, s.safeRows().size());
        assertEquals("0", s.safeRows().get(0).getId());
        assertNull(s.safeRows().get(0).getPinned());
    }

    /**
     * session create / rename 文本。
     */
    @Test
    void mapSessionMutation_shouldParseCreateAndRename() {
        DreaminaCliStructuredPayloadMapper mapper = new DreaminaCliStructuredPayloadMapper();
        DreaminaCliResult created = DreaminaCliResult.builder()
            .stdout("Created session \"cli-smoke-1778855407\" (ID: 13069437163532)\n")
            .stderr("")
            .exitCode(0)
            .success(true)
            .parsed(DreaminaParsedFields.builder().build())
            .build();
        DreaminaSessionMutation c = mapper.mapSessionMutation(created).getBody();
        assertEquals(DreaminaSessionMutation.Kind.CREATE, c.getKind());
        assertEquals("13069437163532", c.getSessionId());

        DreaminaCliResult renamed = DreaminaCliResult.builder()
            .stdout("Renamed session 13069437163532 to \"cli-smoke-renamed\"\n")
            .stderr("")
            .exitCode(0)
            .success(true)
            .parsed(DreaminaParsedFields.builder().build())
            .build();
        DreaminaSessionMutation r = mapper.mapSessionMutation(renamed).getBody();
        assertEquals(DreaminaSessionMutation.Kind.RENAME, r.getKind());
        assertEquals("cli-smoke-renamed", r.getSessionName());
    }

    /**
     * help 纯文本 → 全文见 {@link DreaminaCliResponse#getCombinedText()}。
     */
    @Test
    void mapHelp_shouldWrapStdoutAsTextOutput() {
        String helpBody = """
            Usage:
              dreamina [flags]

            即梦 official AIGC CLI tool for login, account, and generation workflows
            """;
        DreaminaCliResult raw = DreaminaCliResult.builder()
            .stdout(helpBody)
            .stderr("")
            .exitCode(0)
            .success(true)
            .parsed(DreaminaParsedFields.builder().build())
            .build();
        DreaminaCliResponse<DreaminaHelp> response = new DreaminaCliStructuredPayloadMapper().mapHelp(null, raw);
        assertNotNull(response.getBody());
        assertTrue(response.getCombinedText().contains("即梦 official AIGC CLI tool"));
        assertEquals(helpBody, response.getStdout());
    }

    /**
     * logout / relogin 文本命令。
     */
    @Test
    void mapLogoutAndRelogin_shouldWrapText() {
        DreaminaCliStructuredPayloadMapper mapper = new DreaminaCliStructuredPayloadMapper();
        DreaminaCliResult raw = DreaminaCliResult.builder()
            .stdout("ok\n")
            .stderr("")
            .exitCode(0)
            .success(true)
            .parsed(DreaminaParsedFields.builder().build())
            .build();
        DreaminaCliResponse<DreaminaLogout> logoutResponse = mapper.mapLogout(raw);
        assertEquals("ok\n", logoutResponse.getStdout());
        DreaminaCliResponse<DreaminaRelogin> reloginResponse = mapper.mapRelogin(raw);
        assertEquals("ok\n", reloginResponse.getStdout());
    }

    /**
     * checklogin 成功时可能无 stdout（空输出）。
     */
    @Test
    void mapCheckLogin_shouldMarkEmptyOutput() {
        DreaminaCliResult raw = DreaminaCliResult.builder()
            .stdout("")
            .stderr("")
            .exitCode(0)
            .success(true)
            .parsed(DreaminaParsedFields.builder().build())
            .build();
        DreaminaCliResponse<DreaminaCheckLogin> response =
            new DreaminaCliStructuredPayloadMapper().mapCheckLogin(raw);
        assertNull(response.getBody());
        assertTrue(response.getCombinedText().isBlank());
    }

    /**
     * checklogin 带 JSON 时仍走 Jackson。
     */
    @Test
    void mapCheckLogin_shouldReadJsonPayload() {
        DreaminaCliResult raw = DreaminaCliResult.builder()
            .stdout("{\"gen_status\":\"success\",\"message\":\"mock checklogin ok\"}")
            .stderr("")
            .exitCode(0)
            .success(true)
            .parsed(DreaminaParsedFields.builder().build())
            .build();
        DreaminaCheckLogin c = new DreaminaCliStructuredPayloadMapper().mapCheckLogin(raw).getBody();
        assertNotNull(c);
        assertEquals("success", c.getGenStatus());
        assertEquals("mock checklogin ok", c.getMessage());
    }

    /**
     * logout 文本 → localSessionCleared。
     */
    @Test
    void mapLogout_shouldDetectClearedMessage() {
        DreaminaCliResult raw = DreaminaCliResult.builder()
            .stdout("已清除本地登录态。\n")
            .stderr("")
            .exitCode(0)
            .success(true)
            .parsed(DreaminaParsedFields.builder().build())
            .build();
        DreaminaLogout logout = new DreaminaCliStructuredPayloadMapper().mapLogout(raw).getBody();
        assertEquals(Boolean.TRUE, logout.getLocalSessionCleared());
    }

    /**
     * relogin 键值对文本 → Device Flow 对象。
     */
    @Test
    void mapRelogin_shouldParseDeviceFlowTextLines() {
        DreaminaCliResult raw = DreaminaCliResult.builder()
            .stdout(
                """
                请使用浏览器完成 OAuth Device Flow 登录。
                verification_uri: https://jimeng.jianying.com/ai-tool/cli-auth
                user_code: 88d38543ef407cb0a01a61088ec0d32c
                device_code: 662eef8f79b0ee3c20d7222c5ec28ed3
                poll_interval: 1s
                expires_at: 2026-05-26T05:38:58Z
                """)
            .stderr("")
            .exitCode(0)
            .success(true)
            .parsed(DreaminaParsedFields.builder().build())
            .build();
        DreaminaRelogin relogin = new DreaminaCliStructuredPayloadMapper().mapRelogin(raw).getBody();
        assertTrue(relogin.needsCheckLogin());
        assertEquals("662eef8f79b0ee3c20d7222c5ec28ed3", relogin.getDevice().getDeviceCode());
        assertEquals("1s", relogin.getDevice().getPollInterval());
        assertEquals("2026-05-26T05:38:58Z", relogin.getDevice().getExpiresAt());
    }

    /**
     * {@code login --headless} 已登录：仅一行复用提示，无 JSON、无账户键值对。
     */
    @Test
    void mapLogin_headlessShouldParseReuseOnlyText() {
        DreaminaCliResult raw = DreaminaCliResult.builder()
            .stdout("已复用当前本地 OAuth 登录态。\n")
            .stderr("")
            .exitCode(0)
            .success(true)
            .parsed(DreaminaParsedFields.builder().build())
            .build();
        DreaminaLogin login = new DreaminaCliStructuredPayloadMapper().mapLogin(raw).getBody();
        assertEquals(Boolean.TRUE, login.getOauthSessionReused());
        assertTrue(login.isOAuthReuseOnly());
        assertNull(login.getAccount());
        assertFalse(login.getDevice() != null && login.getDevice().isMaterialPresent());
    }

    /**
     * 本机 {@code dreamina login} 复用 OAuth + 账户键值对（2026-05 采集）。
     */
    @Test
    void mapLogin_shouldParseLocalCliAccountBlock() {
        DreaminaCliResult raw = DreaminaCliResult.builder()
            .stdout(
                """
                已复用当前本地 OAuth 登录态。
                当前登录账户信息：
                user_id: 1552973852847448
                vip_level: maestro
                total_credit: 4388
                """)
            .stderr("")
            .exitCode(0)
            .success(true)
            .parsed(DreaminaParsedFields.builder().build())
            .build();
        DreaminaLogin login = new DreaminaCliStructuredPayloadMapper().mapLogin(raw).getBody();
        assertEquals(4388L, login.getAccount().getTotalCredit().longValue());
        assertEquals("maestro", login.getAccount().getVipLevel());
    }

    /**
     * 本机 {@code dreamina session list -n 5} 表格（长 ID + 中文会话名）。
     */
    @Test
    void mapSessionList_shouldParseLocalCliTable() {
        DreaminaCliResult raw = DreaminaCliResult.builder()
            .stdout(
                """
                ID              NAME                    PINNED  UPDATED_AT
                --------------  ----------------------  ------  ----------------
                0               default                 Yes     2026-05-18 11:10
                13525782977036  图片去水印调文字清晰度  No      2026-05-26 09:44
                13486731497484  通过照片生成类似视频    No      2026-05-25 13:39
                """)
            .stderr("")
            .exitCode(0)
            .success(true)
            .parsed(DreaminaParsedFields.builder().build())
            .build();
        DreaminaSessionList list = new DreaminaCliStructuredPayloadMapper().mapSessionList(raw).getBody();
        assertEquals(3, list.safeRows().size());
        assertEquals("0", list.safeRows().get(0).getId());
        assertEquals("default", list.safeRows().get(0).getName());
        assertEquals("Yes", list.safeRows().get(0).getPinned());
        assertEquals("13525782977036", list.safeRows().get(1).getId());
        assertEquals("图片去水印调文字清晰度", list.safeRows().get(1).getName());
    }

    /**
     * login 复用 OAuth 文本 → 账户键值对对象。
     */
    @Test
    void mapLogin_shouldParseReusedOAuthAccountLines() {
        DreaminaCliResult raw = DreaminaCliResult.builder()
            .stdout(
                """
                已复用当前本地 OAuth 登录态。
                当前登录账户信息：
                user_id: 1552973852847448
                vip_level: maestro
                total_credit: 4391
                """)
            .stderr("")
            .exitCode(0)
            .success(true)
            .parsed(DreaminaParsedFields.builder().build())
            .build();
        DreaminaLogin login = new DreaminaCliStructuredPayloadMapper().mapLogin(raw).getBody();
        assertEquals(Boolean.TRUE, login.getOauthSessionReused());
        assertNotNull(login.getAccount());
        assertEquals(1552973852847448L, login.getAccount().getUserId().longValue());
        assertEquals("maestro", login.getAccount().getVipLevel());
        assertEquals(4391L, login.getAccount().getTotalCredit().longValue());
        assertTrue(login.hasAccount());
    }

    /**
     * 未登录：{@code user_credit} 常为 exit 1、无 JSON（见 {@code .cli-audit/exec_user_credit.txt}）。
     */
    @Test
    void mapUserCredit_unauthenticatedEmpty_shouldReturnNullBody() {
        DreaminaCliResult raw = DreaminaCliResult.builder()
            .stdout("")
            .stderr("")
            .exitCode(1)
            .success(false)
            .parsed(DreaminaParsedFields.builder().build())
            .build();
        DreaminaCliResponse<DreaminaUserCredit> response =
            new DreaminaCliStructuredPayloadMapper().mapUserCredit(raw);
        assertNull(response.getBody());
        assertEquals(1, response.getExitCode());
    }

    /**
     * 无本地登录态时 {@code logout} 提示（exit 0，非「已清除」语义）。
     */
    @Test
    void mapLogout_noLocalSession_shouldNotMarkCleared() {
        DreaminaCliResult raw = DreaminaCliResult.builder()
            .stdout("当前没有本地登录态。\n")
            .stderr("")
            .exitCode(0)
            .success(true)
            .parsed(DreaminaParsedFields.builder().build())
            .build();
        DreaminaLogout logout = new DreaminaCliStructuredPayloadMapper().mapLogout(raw).getBody();
        assertNotNull(logout);
        assertNull(logout.getLocalSessionCleared());
    }

    /**
     * 未登录/无效 submit_id 时 {@code query_result} 可能 exit 0 且无 JSON 体。
     */
    @Test
    void mapQueryResult_emptyStdout_shouldReturnNullBody() {
        DreaminaCliResult raw = DreaminaCliResult.builder()
            .stdout("")
            .stderr("")
            .exitCode(0)
            .success(true)
            .parsed(DreaminaParsedFields.builder().build())
            .build();
        assertNull(new DreaminaCliStructuredPayloadMapper().mapQueryResult(raw).getBody());
    }

    /**
     * 本机 {@code .cli-audit/list_task_n3.txt} 完整三条任务（commerce_info + 多图 result_json）。
     */
    @Test
    void mapTaskList_shouldParseCliAuditListTaskN3() throws Exception {
        java.nio.file.Path audit =
            java.nio.file.Paths.get(".cli-audit/list_task_n3.txt");
        org.junit.jupiter.api.Assumptions.assumeTrue(
            java.nio.file.Files.isRegularFile(audit),
            "missing .cli-audit/list_task_n3.txt — run audit script locally");
        String file = java.nio.file.Files.readString(audit);
        int start = file.indexOf('[');
        int end = file.lastIndexOf(']');
        org.junit.jupiter.api.Assumptions.assumeTrue(start >= 0 && end > start);
        String json = file.substring(start, end + 1);
        DreaminaCliResult raw = DreaminaCliResult.builder()
            .stdout(json)
            .stderr("")
            .exitCode(0)
            .success(true)
            .parsed(DreaminaParsedFields.builder().build())
            .build();
        DreaminaCliResponse<List<DreaminaTaskItem>> response =
            new DreaminaCliStructuredPayloadMapper().mapTaskList(raw);
        List<DreaminaTaskItem> tasks = response.getBody();
        assertNotNull(tasks);
        assertEquals(3, tasks.size());
        assertEquals("c3c0774f-6715-4c0a-b044-f961acf38314", tasks.get(0).getSubmitId());
        assertEquals("text2image", tasks.get(0).getGenTaskType());
        assertEquals("success", tasks.get(0).getGenStatus());
        assertEquals(3520, tasks.get(0).getResultJson().safeImages().get(0).getWidth());
        assertEquals(4, tasks.get(1).resolveCreditCount().longValue());
        assertEquals(4, tasks.get(2).getResultJson().safeImages().size());
        assertEquals("image_basic_generate_plus", tasks.get(2).getCommerceInfo().safeTriplets().get(0).getBenefitType());
    }

    /**
     * device login JSON → DeviceLogin DTO。
     */
    @Test
    void mapDeviceLogin_shouldReadSnakeCaseKeys() {
        DreaminaCliResult raw = DreaminaCliResult.builder()
            .stdout(
                """
                {"device_code":"abc","verification_uri":"https://example","user_code":"ABCD"}
                """)
            .stderr("")
            .exitCode(0)
            .success(true)
            .parsed(DreaminaParsedFields.builder().build())
            .build();
        DreaminaDeviceLogin d = new DreaminaCliStructuredPayloadMapper().mapDeviceLogin(raw).getBody();
        assertNotNull(d);
        assertEquals("abc", d.getDeviceCode());
        assertEquals("https://example", d.getVerificationUri());
        assertEquals("ABCD", d.getUserCode());
    }

    /**
     * 2026-05 交互式审计：{@code exec_query_refresh_beefb889.txt}（frames2video querying）。
     */
    @Test
    void mapQueryResult_fromAuditQueryingRefresh() throws Exception {
        DreaminaQueryResult q = MAPPER.mapQueryResult(jsonStdout(loadAuditFixture("query_refresh_querying.json"))).getBody();
        assertNotNull(q);
        assertEquals("beefb889-845f-4dde-bdf7-c85db895d1c9", q.getSubmitId());
        assertEquals("季节从夏到秋的变化", q.getPrompt());
        assertEquals("202605261449561921680000980034E40", q.getLogid());
        assertTrue(q.isGenQuerying());
        assertFalse(q.isGenSuccess());
        assertEquals(70L, q.getCreditCount().longValue());
        assertEquals("Generating", q.getQueueInfo().getQueueStatus());
        assertFalse(q.isQueueFinished());
        assertNotNull(q.getQueueInfo().getParsedDebugInfo());
        assertEquals("dreamina_fusion_video40_pro_vision", q.getQueueInfo().getParsedDebugInfo().getDreaminaMatrixQueueName());
        assertTrue(q.images().isEmpty());
        assertTrue(q.videos().isEmpty());
    }

    /**
     * 2026-05 交互式审计：{@code exec_query_refresh_262760ae.txt}（text2image success + image_url）。
     */
    @Test
    void mapQueryResult_fromAuditSuccessImageRefresh() throws Exception {
        DreaminaQueryResult q = MAPPER.mapQueryResult(jsonStdout(loadAuditFixture("query_refresh_success_image.json"))).getBody();
        assertNotNull(q);
        assertTrue(q.isGenSuccess());
        assertEquals(3L, q.getCreditCount().longValue());
        assertEquals(1, q.images().size());
        assertEquals(2048, q.images().get(0).getWidth().intValue());
        assertNotNull(q.firstImageUrl());
        assertTrue(q.firstImageUrl().contains("p26-dreamina-sign.byteimg.com"));
        assertTrue(q.isQueueFinished());
        assertEquals("high_aes_general_v50", q.getQueueInfo().getParsedDebugInfo().getQueueName());
    }

    /**
     * 2026-05 交互式审计：{@code exec_query_refresh_7045230a.txt}（multiframe2video success + 小数 duration）。
     */
    @Test
    void mapQueryResult_fromAuditSuccessVideoRefresh() throws Exception {
        DreaminaQueryResult q = MAPPER.mapQueryResult(jsonStdout(loadAuditFixture("query_refresh_success_video.json"))).getBody();
        assertNotNull(q);
        assertTrue(q.isGenSuccess());
        assertTrue(q.images().isEmpty());
        assertEquals(1, q.videos().size());
        var video = q.videos().get(0);
        assertEquals(24, video.getFps().intValue());
        assertEquals("mp4", video.getFormat());
        assertEquals(3.208, video.getDuration(), 0.001);
        assertEquals(960, video.getWidth().intValue());
        assertNotNull(q.firstVideoUrl());
        assertEquals(6L, q.getCreditCount().longValue());
        assertEquals(
            "DreaminaFusion:Video3_fast_multi_frame_720p",
            q.getQueueInfo().getParsedDebugInfo().getDreaminaMatrixReqKey());
    }

    /**
     * 2026-05 交互式审计：{@code exec_text2image_submit.json.txt}（submit-only）。
     */
    @Test
    void mapGenerateSubmit_fromAuditText2ImageSubmit() throws Exception {
        DreaminaGenerateSubmit dto = MAPPER.mapGenerateSubmit(jsonStdout(loadAuditFixture("text2image_submit.json"))).getBody();
        assertNotNull(dto);
        assertEquals("262760ae-2694-439f-9258-7a1fb20c33d4", dto.getSubmitId());
        assertEquals("20260526144915192168000098601109F", dto.getLogid());
        assertEquals("querying", dto.getGenStatus());
        assertEquals(3L, dto.getCreditCount().longValue());
    }

    /**
     * 2026-05 交互式审计：{@code exec_list_task_refresh.txt} 中 multiframe2video 条目（无 URL、含 fps/format/duration）。
     */
    @Test
    void mapTaskList_fromAuditListTaskMultiframe() throws Exception {
        List<DreaminaTaskItem> tasks =
            MAPPER.mapTaskList(jsonStdout(loadAuditFixture("list_task_refresh_multiframe.json"))).getBody();
        assertNotNull(tasks);
        assertEquals(1, tasks.size());
        var item = tasks.get(0);
        assertEquals("7045230a-b96f-4470-8212-b424a609782c", item.getSubmitId());
        assertEquals("multiframe2video", item.getGenTaskType());
        assertEquals("success", item.getGenStatus());
        assertEquals(6L, item.resolveCreditCount().longValue());
        var video = item.getResultJson().safeVideos().get(0);
        assertEquals(24, video.getFps().intValue());
        assertEquals("mp4", video.getFormat());
        assertEquals(3.208, video.getDuration(), 0.001);
        assertEquals("basic_video_operation_vgfm_v_three", item.getCommerceInfo().safeTriplets().get(0).getBenefitType());
    }
}
