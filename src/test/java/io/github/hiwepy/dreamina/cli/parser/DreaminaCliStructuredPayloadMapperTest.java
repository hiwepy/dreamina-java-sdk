package io.github.hiwepy.dreamina.cli.parser;

import io.github.hiwepy.dreamina.cli.DreaminaDeviceLoginResult;
import io.github.hiwepy.dreamina.cli.DreaminaCliResult;
import io.github.hiwepy.dreamina.cli.DreaminaQueryResult;
import io.github.hiwepy.dreamina.cli.DreaminaSessionListResult;
import io.github.hiwepy.dreamina.cli.DreaminaSessionMutationResult;
import io.github.hiwepy.dreamina.cli.DreaminaSessionSearchResult;
import io.github.hiwepy.dreamina.cli.DreaminaTaskListResult;
import io.github.hiwepy.dreamina.cli.DreaminaVersionResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link DreaminaCliStructuredPayloadMapper} 单元测试：复用真实 CLI JSON / 表格快照片段。
 *
 * @author wandl
 * @since 1.0.0
 */
class DreaminaCliStructuredPayloadMapperTest {

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
        DreaminaVersionResult v = mapper.mapVersion(raw);
        assertEquals("c58a6a2-dirty", v.getVersion());
        assertEquals("c58a6a2", v.getCommit());
        assertEquals("2026-05-07T09:52:59Z", v.getBuildTime());
        assertNotNull(v.getJson());
        assertNull(v.getRawTextFallback());
    }

    /**
     * list_task JSON 数组 → tasks / taskCount。
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
        DreaminaTaskListResult dto = new DreaminaCliStructuredPayloadMapper().mapTaskList(raw);
        assertEquals(2, dto.getTaskCount().intValue());
        assertNotNull(dto.getTasks());
        assertEquals("a", dto.getTasks().get(0).get("submit_id").asText());
    }

    /**
     * query_result JSON → queue_info / result_json。
     */
    @Test
    void mapQueryResult_shouldExtractNestedNodes() {
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
        DreaminaQueryResult q = new DreaminaCliStructuredPayloadMapper().mapQueryResult(raw);
        assertEquals("2fcc4089-f0d3-479c-a42f-f73c838cc626", q.getSubmitId());
        assertEquals("success", q.getGenStatus());
        assertNotNull(q.getQueueInfo());
        assertEquals("Finish", q.getQueueInfo().get("queue_status").asText());
        assertNotNull(q.getResultJson());
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
        DreaminaSessionListResult list = new DreaminaCliStructuredPayloadMapper().mapSessionList(raw);
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
        DreaminaSessionSearchResult s = new DreaminaCliStructuredPayloadMapper().mapSessionSearch("default", raw);
        assertEquals(1, s.getMatches().size());
        assertEquals("0", s.getMatches().get(0).getId());
        assertNull(s.getMatches().get(0).getPinned());
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
        DreaminaSessionMutationResult c = mapper.mapSessionMutation(created);
        assertEquals(DreaminaSessionMutationResult.Kind.CREATE, c.getKind());
        assertEquals("13069437163532", c.getSessionId());

        DreaminaCliResult renamed = DreaminaCliResult.builder()
            .stdout("Renamed session 13069437163532 to \"cli-smoke-renamed\"\n")
            .stderr("")
            .exitCode(0)
            .success(true)
            .parsed(DreaminaParsedFields.builder().build())
            .build();
        DreaminaSessionMutationResult r = mapper.mapSessionMutation(renamed);
        assertEquals(DreaminaSessionMutationResult.Kind.RENAME, r.getKind());
        assertEquals("cli-smoke-renamed", r.getSessionName());
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
        DreaminaDeviceLoginResult d = new DreaminaCliStructuredPayloadMapper().mapDeviceLogin(raw);
        assertEquals("abc", d.getDeviceCode());
        assertEquals("https://example", d.getVerificationUri());
        assertEquals("ABCD", d.getUserCode());
    }
}
