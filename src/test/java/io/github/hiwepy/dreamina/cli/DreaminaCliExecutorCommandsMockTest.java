package io.github.hiwepy.dreamina.cli;

import io.github.hiwepy.dreamina.cli.opts.DreaminaFrames2VideoRequest;
import io.github.hiwepy.dreamina.cli.opts.DreaminaImage2ImageRequest;
import io.github.hiwepy.dreamina.cli.opts.DreaminaImage2VideoRequest;
import io.github.hiwepy.dreamina.cli.opts.DreaminaImageModelVersion;
import io.github.hiwepy.dreamina.cli.opts.DreaminaImageResolutionType;
import io.github.hiwepy.dreamina.cli.opts.DreaminaImageUpscaleRequest;
import io.github.hiwepy.dreamina.cli.opts.DreaminaListTaskRequest;
import io.github.hiwepy.dreamina.cli.opts.DreaminaMultiframe2VideoRequest;
import io.github.hiwepy.dreamina.cli.opts.DreaminaMultimodal2VideoRequest;
import io.github.hiwepy.dreamina.cli.opts.DreaminaQueryResultRequest;
import io.github.hiwepy.dreamina.cli.opts.DreaminaRatio;
import io.github.hiwepy.dreamina.cli.opts.DreaminaText2ImageRequest;
import io.github.hiwepy.dreamina.cli.opts.DreaminaText2VideoRequest;
import io.github.hiwepy.dreamina.cli.opts.DreaminaVideoModelVersion;
import io.github.hiwepy.dreamina.cli.opts.DreaminaVideoResolutionType;
import io.github.hiwepy.dreamina.cli.model.DreaminaCheckLogin;
import io.github.hiwepy.dreamina.cli.model.DreaminaLogin;
import io.github.hiwepy.dreamina.cli.model.DreaminaLogout;
import io.github.hiwepy.dreamina.cli.model.DreaminaRelogin;
import io.github.hiwepy.dreamina.cli.model.DreaminaTaskItem;
import io.github.hiwepy.dreamina.cli.support.MockDreaminaCli;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 全部 CLI 子命令 mock 测试：每条指令一个测试方法。 */
class DreaminaCliExecutorCommandsMockTest {

    private MockDreaminaCli mockCli;
    private DreaminaCliExecutor executor;
    private Path tinyPng;

    @BeforeEach
    void setUp() throws Exception {
        mockCli = MockDreaminaCli.install();
        mockCli.resetLog();
        executor = mockCli.newExecutor();
        tinyPng = mockCli.newTinyPng("tiny.png");
    }

    @Test void helpCommand() throws Exception {
        assertTrue(executor.help().isSuccess());
        assertTrue(mockCli.lastInvocation().startsWith("help"));
        assertNotNull(executor.helpInfo().getCombinedText());
    }

    @Test void helpSubcommandText2image() throws Exception {
        assertTrue(executor.help("text2image").isSuccess());
        assertNotNull(executor.helpInfo("text2image", List.of("-h")).getBody());
    }

    @Test void versionCommand() throws Exception {
        assertTrue(executor.version().isSuccess());
        var v = executor.versionInfo().getBody();
        assertEquals("c58a6a2-dirty", v.getVersion());
        assertEquals("c58a6a2", v.getCommit());
        assertEquals("2026-05-07T09:52:59Z", v.getBuildTime());
    }

    @Test void userCreditCommand() throws Exception {
        assertTrue(executor.userCredit().isSuccess());
        assertEquals(4388L, executor.userCreditInfo().getBody().getTotalCredit());
        assertEquals("", executor.userCreditInfo().getBody().getUserName());
        assertEquals("maestro", executor.userCreditInfo().getBody().getVipLevel());
    }

    @Test void loginCommand() throws Exception {
        assertTrue(executor.login().isSuccess());
        assertTrue(mockCli.lastInvocation().startsWith("login"));
        DreaminaLogin login = executor.loginInfo().getBody();
        assertEquals(Boolean.TRUE, login.getOauthSessionReused());
        assertNotNull(login.getAccount());
        assertEquals(1552973852847448L, login.getAccount().getUserId().longValue());
        assertEquals("maestro", login.getAccount().getVipLevel());
        assertEquals(4391L, login.getAccount().getTotalCredit().longValue());
    }

    @Test void loginHeadlessCommand() throws Exception {
        assertTrue(executor.loginHeadless().isSuccess());
        DreaminaLogin headless = executor.loginHeadlessInfo().getBody();
        assertTrue(headless.isOAuthReuseOnly());
        assertEquals(Boolean.TRUE, headless.getOauthSessionReused());
        assertNull(headless.getAccount());
        assertNull(headless.getDevice());
    }

    @Test void loginCheckloginCommand() throws Exception {
        assertTrue(executor.checkLogin("dev-mock", 30).isSuccess());
        String inv = mockCli.lastInvocation();
        assertTrue(inv.contains("checklogin") && inv.contains("--device_code=dev-mock"));
        DreaminaCliResponse<DreaminaCheckLogin> checkResponse = executor.checkLoginInfo("dev-mock", 30);
        assertTrue(checkResponse.getCombinedText().isBlank());
        assertNull(checkResponse.getBody());
    }

    @Test void logoutCommand() throws Exception {
        assertTrue(executor.logout(List.of("--verbose")).isSuccess());
        DreaminaCliResponse<DreaminaLogout> logoutResponse = executor.logoutInfo(List.of("--verbose"));
        assertEquals(Boolean.TRUE, logoutResponse.getBody().getLocalSessionCleared());
        assertTrue(logoutResponse.getCombinedText().contains("已清除"));
    }

    @Test void reloginCommand() throws Exception {
        assertTrue(executor.relogin().isSuccess());
        DreaminaRelogin relogin = executor.reloginInfo().getBody();
        assertEquals(Boolean.TRUE, relogin.getRequiresBrowserOAuth());
        assertTrue(relogin.needsCheckLogin());
        assertEquals("662eef8f79b0ee3c20d7222c5ec28ed3", relogin.getDevice().getDeviceCode());
        assertEquals("88d38543ef407cb0a01a61088ec0d32c", relogin.getDevice().getUserCode());
        assertEquals("1s", relogin.getDevice().getPollInterval());
    }

    @Test void sessionBareCommand() throws Exception {
        assertTrue(executor.session().isSuccess());
        assertTrue(executor.sessionInfo().getCombinedText().contains("Manage Dreamina sessions"));
        assertEquals("session", executor.sessionInfo().getBody().getTopic());
        assertTrue(executor.session(List.of("-h")).isSuccess());
    }

    @Test void sessionCreateCommand() throws Exception {
        assertTrue(executor.sessionCreate(List.of("mock-name")).isSuccess());
        assertEquals("10001", executor.sessionCreateInfo(List.of("mock-name")).getBody().getSessionId());
    }

    @Test void sessionListCommand() throws Exception {
        assertTrue(executor.sessionList(List.of("-n=5")).isSuccess());
        assertTrue(executor.sessionListInfo(List.of("-n=5")).getBody().getRows().size() >= 1);
    }

    @Test void sessionListAliasCommand() throws Exception {
        assertTrue(executor.sessionLs(List.of("-n=100")).isSuccess());
        assertTrue(mockCli.lastInvocation().startsWith("session ls"));
        assertTrue(executor.sessionLsInfo(List.of("-n=100")).getBody().getRows().size() >= 1);
    }

    @Test void sessionSearchCommand() throws Exception {
        assertTrue(executor.sessionSearch("mock", List.of()).isSuccess());
        assertEquals(1, executor.sessionSearchInfo("mock").getBody().safeRows().size());
    }

    @Test void sessionFindAliasCommand() throws Exception {
        assertTrue(executor.sessionFind("mock", List.of()).isSuccess());
        assertTrue(mockCli.lastInvocation().startsWith("session find"));
        assertEquals(1, executor.sessionFindInfo("mock", List.of()).getBody().safeRows().size());
    }

    @Test void sessionRenameCommand() throws Exception {
        assertTrue(executor.sessionRename("10001", "new-name").isSuccess());
        assertEquals("mock-renamed", executor.sessionRenameInfo("10001", "new-name").getBody().getSessionName());
    }

    @Test void sessionUpdateAliasCommand() throws Exception {
        assertTrue(executor.sessionUpdate("10001", "new-name").isSuccess());
        assertTrue(mockCli.lastInvocation().startsWith("session update"));
        assertEquals("mock-renamed", executor.sessionUpdateInfo("10001", "new-name", List.of())
            .getBody().getSessionName());
    }

    @Test void sessionDeleteCommand() throws Exception {
        DreaminaCliResult deleteRaw = executor.sessionDelete("10001");
        assertTrue(deleteRaw.isSuccess());
        assertTrue(executor.structuredPayloadMapper().mapSessionDelete(deleteRaw).getBody().isDeleted());
    }

    @Test void sessionRmAliasCommand() throws Exception {
        assertTrue(executor.sessionRm("10001").isSuccess());
        assertTrue(mockCli.lastInvocation().startsWith("session rm"));
    }

    @Test void listTaskCommand() throws Exception {
        assertTrue(executor.listTask(List.of("--gen_status=success")).isSuccess());
        List<DreaminaTaskItem> tasks = executor.listTaskInfo().getBody();
        assertEquals(1, tasks.size());
        assertEquals("mock-submit-1", tasks.get(0).getSubmitId());
        assertEquals(0L, tasks.get(0).resolveCreditCount().longValue());
        assertEquals("image_uhd_4k", tasks.get(0).getCommerceInfo().safeTriplets().get(0).getBenefitType());
    }

    @Test void listTaskRequestCommand() throws Exception {
        DreaminaListTaskRequest request = DreaminaListTaskRequest.builder()
            .genStatus("success").genTaskType("text2image").limit(10).offset(0).build();
        assertTrue(executor.listTask(request).isSuccess());
        assertNotNull(executor.listTaskInfo(request).getBody());
    }

    @Test void queryResultCommand() throws Exception {
        assertTrue(executor.queryResult("mock-submit-1").isSuccess());
        var q = executor.queryResultInfo("mock-submit-1").getBody();
        assertEquals("success", q.getGenStatus());
        assertTrue(q.isGenSuccess());
        assertEquals(3L, q.getCreditCount().longValue());
        assertEquals(2048, q.images().get(0).getWidth().intValue());
        assertEquals("Finish", q.getQueueInfo().getQueueStatus());
        assertTrue(q.isQueueFinished());
    }

    @Test void queryResultRequestCommand() throws Exception {
        DreaminaQueryResultRequest request = DreaminaQueryResultRequest.builder()
            .submitId("mock-submit-1").downloadDir("./downloads").build();
        assertTrue(executor.queryResult(request).isSuccess());
        assertNotNull(executor.queryResultInfo(request).getBody());
    }

    @Test void text2imageCommand() throws Exception {
        assertTrue(executor.text2Image("a cat", List.of("--poll=0")).isSuccess());
        DreaminaText2ImageRequest request = DreaminaText2ImageRequest.builder()
            .prompt("a cat").ratio(DreaminaRatio.RATIO_1_1).pollSeconds(0).build();
        var submit = executor.text2ImageSubmit(request).getBody();
        assertEquals("mock-gen-1", submit.getSubmitId());
        assertEquals("querying", submit.getGenStatus());
        assertEquals(3L, submit.getCreditCount().longValue());
        assertEquals("202605260533251720170000026033C60", submit.getLogid());
    }

    @Test void image2imageCommand() throws Exception {
        assertTrue(executor.image2Image(tinyPng.toString(), "watercolor", List.of("--poll=0")).isSuccess());
        DreaminaImage2ImageRequest request = DreaminaImage2ImageRequest.builder()
            .image(tinyPng.toString()).prompt("watercolor")
            .modelVersion(DreaminaImageModelVersion.MODEL_4_5)
            .resolutionType(DreaminaImageResolutionType.RESOLUTION_2K).pollSeconds(0).build();
        assertNotNull(executor.image2ImageSubmit(request).getBody().getSubmitId());
    }

    @Test void imageUpscaleCommand() throws Exception {
        assertTrue(executor.imageUpscale(List.of("--image=" + tinyPng, "--poll=0")).isSuccess());
        DreaminaImageUpscaleRequest request = DreaminaImageUpscaleRequest.builder()
            .imagePath(tinyPng.toString()).resolutionType(DreaminaImageResolutionType.RESOLUTION_2K)
            .pollSeconds(0).build();
        assertNotNull(executor.imageUpscaleSubmit(request).getBody().getSubmitId());
    }

    @Test void text2videoCommand() throws Exception {
        assertTrue(executor.text2video("run", List.of("--poll=0")).isSuccess());
        DreaminaText2VideoRequest request = DreaminaText2VideoRequest.builder()
            .prompt("run").durationSeconds(5).ratio(DreaminaRatio.RATIO_16_9)
            .videoResolution(DreaminaVideoResolutionType.RESOLUTION_720P).pollSeconds(0).build();
        assertNotNull(executor.text2VideoSubmit(request).getBody().getSubmitId());
    }

    @Test void image2videoCommand() throws Exception {
        assertTrue(executor.image2video(tinyPng.toString(), "push in", List.of("--poll=0")).isSuccess());
        DreaminaImage2VideoRequest request = DreaminaImage2VideoRequest.builder()
            .imagePath(tinyPng.toString()).prompt("push in").durationSeconds(5).pollSeconds(0).build();
        assertNotNull(executor.image2VideoSubmit(request).getBody().getSubmitId());
    }

    @Test void frames2videoCommand() throws Exception {
        assertTrue(executor.frames2video(List.of(
            "--first=" + tinyPng, "--last=" + tinyPng, "--prompt=transition", "--poll=0")).isSuccess());
        DreaminaFrames2VideoRequest request = DreaminaFrames2VideoRequest.builder()
            .firstImagePath(tinyPng.toString()).lastImagePath(tinyPng.toString())
            .prompt("transition").durationSeconds(5).pollSeconds(0).build();
        assertNotNull(executor.frames2VideoSubmit(request).getBody().getSubmitId());
    }

    @Test void multiframe2videoCommand() throws Exception {
        Path second = mockCli.newTinyPng("tiny2.png");
        assertTrue(executor.multiframe2video(List.of(
            "--images=" + tinyPng + "," + second, "--prompt=story", "--duration=3", "--poll=0")).isSuccess());
        DreaminaMultiframe2VideoRequest request = DreaminaMultiframe2VideoRequest.builder()
            .image(tinyPng.toString()).image(second.toString()).prompt("story")
            .durationSeconds(3.0).pollSeconds(0).build();
        assertNotNull(executor.multiframe2VideoSubmit(request).getBody().getSubmitId());
    }

    @Test void multimodal2videoCommand() throws Exception {
        assertTrue(executor.multimodal2video(List.of(
            "--image=" + tinyPng, "--prompt=cinematic", "--poll=0")).isSuccess());
        DreaminaMultimodal2VideoRequest request = DreaminaMultimodal2VideoRequest.builder()
            .image(tinyPng.toString()).prompt("cinematic").durationSeconds(5)
            .modelVersion(DreaminaVideoModelVersion.SEEDANCE_2_0_FAST).pollSeconds(0).build();
        assertNotNull(executor.multimodal2VideoSubmit(request).getBody().getSubmitId());
    }

    @Test void invokeGenericCommand() throws Exception {
        assertTrue(executor.invoke(DreaminaCliSubcommands.Builtin.HELP, Collections.emptyList()).isSuccess());
    }

    @Test void mapOnlyHelpers() {
        DreaminaCliResult jsonRaw = DreaminaCliResult.builder()
            .stdout("{\"submit_id\":\"x\",\"gen_status\":\"querying\"}")
            .stderr("").exitCode(0).success(true).build();
        assertNotNull(executor.mapGenerateSubmitOnly(jsonRaw).getBody());
        assertNotNull(executor.mapQueryResultOnly(jsonRaw).getBody());
        DreaminaCliResult taskListRaw = DreaminaCliResult.builder()
            .stdout("[{\"submit_id\":\"x\",\"gen_status\":\"querying\"}]")
            .stderr("").exitCode(0).success(true).build();
        assertNotNull(executor.mapTaskListOnly(taskListRaw).getBody());
        assertNotNull(executor.mapHelpOnly("help", jsonRaw).getBody());
        assertNotNull(executor.structuredPayloadMapper());
    }
}
